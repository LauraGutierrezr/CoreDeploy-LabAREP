# Lambda Web Framework

**Building and Deploying a Maintainable Application Server**
Author: Laura Valentina Gutierrez Rico· Escuela Colombiana de Ingeniería

A small, dependency-free Java web framework that lets an application
register HTTP `GET` routes as lambdas and serve static files, built on top
of the same sequential (single-threaded) socket server used in the
Networking Lab. This repository contains the framework itself
(`co.edu.escuelaing.webframework`) and a demo application built on it
(`co.edu.escuelaing.app`).

```java
WebFramework.staticfiles("/webroot");
WebFramework.get("/hello", (req, resp) -> "Hello " + req.getValue("name"));
WebFramework.get("/pi", (req, resp) -> String.valueOf(Math.PI));
WebFramework.start();
```

## Table of contents

- [System metaphor](#system-metaphor)
- [Architecture](#architecture)
- [Design decisions](#design-decisions)
- [Project structure](#project-structure)
- [Requirements](#requirements)
- [Building and running locally](#building-and-running-locally)
- [Configuration (environment variables)](#configuration-environment-variables)
- [Using the framework in a new application](#using-the-framework-in-a-new-application)
- [Testing](#testing)
- [Deploying to AWS EC2](#deploying-to-aws-ec2)
- [Evidence](#evidence)
- [Maintainability](#maintainability)
- [Cleanup](#cleanup)

## System metaphor

Think of the framework as **a diner with one order window and a self-serve
pantry**.

Before the diner opens (`WebFramework.start()`), the owner (the
application, `Application.java`) pins a menu to the order window: for each
dish name (`get("/hello", ...)`), a card describing exactly how to make it
(the lambda). The menu can only be written before opening time — once
customers are being served, nobody edits it mid-shift.

There is exactly one window and one cook (`HttpServer`'s sequential
`accept()` loop). One customer places an order, the cook prepares it start
to finish, hands it over, and only then waves the next customer forward.
Nothing is parallelized and nothing is dropped — it is slower under load
than a diner with ten cooks, but there is never a race between two cooks
reaching for the same pan, and mistakes are trivial to trace to exactly one
order.

When an order comes in, the cook first checks the menu (`Router`). If the
dish is listed, they follow that card. If it is not, they check the pantry
shelf (`StaticFileService`) in case the customer just wanted something
off-the-shelf — a printed placemat, a wrapped snack (`index.html`,
`app.js`, `images/logo.png`). If neither the menu nor the pantry has it,
the customer is told plainly the diner doesn't serve that (`404`). If a
cook burns a dish while making it (an exception inside a lambda), the
window still returns *something* coherent to the customer — "the kitchen
had a problem with that order" (`500`) — instead of the whole diner going
dark.

A few knobs are set on a sign by the door before opening, without touching
the kitchen: which door number to use (`PORT`), what greeting the host
uses (`GREETING_PREFIX`), and whether the "staff, come use the back door to
close early" shortcut exists at all (`/shutdown`, wired up only when
`APP_ENV` is not `production`).

## Architecture

```
Browser  --HTTP-->  EC2 security group  -->  HttpServer (sequential accept loop)
                                                   |
                                        path registered as a route?
                                             /                 \
                                          yes                   no
                                           |                     |
                                     Router -> lambda     StaticFileService
                                     (Request, Response)  (classpath jar or
                                       -> String body      STATIC_FILES_PATH)
                                                                  |
                                                          found? 200 : 404
```

Classes, by responsibility:

| Class | Responsibility |
|---|---|
| `WebFramework` | Public static facade: `get()`, `staticfiles()`, `start()`/`start(int)`, `stop()`. The only class application code imports. |
| `HttpService` | Functional interface `(Request, Response) -> String` — what a route lambda implements. |
| `Request` | Path + query parameters for one request; `getValue(name)` reads a query parameter. |
| `Response` | Status code and content type a lambda can set before returning its body. |
| `Router` | Exact-path table of registered `GET` routes (package-private). |
| `StaticFileService` | Resolves a path to file bytes + content type, from the classpath or from `STATIC_FILES_PATH` (package-private). |
| `HttpServer` | The socket layer: one `ServerSocket`, one connection handled at a time, route-then-static-then-404 dispatch, exceptions turned into `500` (package-private). |
| `ContentTypes` | Small hardcoded extension → MIME type table (package-private). |
| `Application` (`co.edu.escuelaing.app`) | The demo app: registers `/hello`, `/pi`, and — outside production — `/shutdown`. |

Everything except `WebFramework`, `HttpService`, `Request` and `Response`
is package-private: an application cannot reach into the router, the
socket loop or the file resolver even if it wanted to. That boundary is
deliberate — see [Maintainability](#maintainability).

## Design decisions

- **Sequential server, on purpose.** Exactly one request is being handled
  end to end at any moment. This keeps the request lifecycle trivial to
  reason about and test (no shared mutable state, no synchronization), at
  the explicit cost of throughput under concurrent load. For a teaching
  framework whose point is routing and configuration, that trade is
  worth it.
- **Exact-path routing only.** The lab's contract is `get(path, lambda)`
  with query parameters read via `getValue()` — no path variables or
  wildcards are required, so `Router` is a plain map instead of a matcher,
  which keeps it easy to verify (a route either exists for a path or it
  doesn't).
- **Static files from the classpath by default.** Because
  `src/main/resources/webroot` is packaged straight into the runnable jar,
  a deployment is exactly one file: copy the jar, start it. The
  `STATIC_FILES_PATH` environment variable can override this with a real
  filesystem directory when you want to edit the front end without
  rebuilding — useful during development, intentionally unset in
  production.
- **A route lambda cannot crash the server.** `HttpServer` wraps every
  call to a route's `handle()` in a `try/catch` and answers `500` on
  failure instead of letting an application bug take down the accept
  loop. A demo route (`/boom` in the integration test) exists specifically
  to prove this.
- **Configuration lives in environment variables, not code.** `PORT`,
  `GREETING_PREFIX`, `APP_ENV` and `STATIC_FILES_PATH` are all read at
  startup. The same jar behaves differently in dev and production without
  a rebuild — which is also how `/shutdown` gets disabled before a public
  deployment (see below).
- **`/shutdown` is a route, not a special case.** It is registered with
  the exact same `WebFramework.get(...)` call as `/hello` or `/pi`, guarded
  by a plain `if (!isProduction)` in `Application.java`. Calling
  `WebFramework.stop()` from inside the handler closes the `ServerSocket`;
  because the server is sequential, that only stops the *next* `accept()`
  — the response to the `/shutdown` request itself is written normally
  first.

## Project structure

```
coredeploy/
├── pom.xml
├── README.md
├── .gitignore
├── deploy/
│   ├── lambda-web-framework.service  
│   └── deploy.sh                    
└── src/
    ├── main/
    │   ├── java/co/edu/escuelaing/
    │   │   ├── webframework/ 
    │   │   └── app/Application.java   
    │   └── resources/webroot/    
    │       ├── index.html
    │       ├── styles.css
    │       ├── app.js
    │       └── images/logo.png
    └── test/
        ├── java/co/edu/escuelaing/webframework/
        └── resources/webroot-test/   
```

## Requirements

- JDK 17+
- Maven 3.8+


## Building and running locally

```bash
git clone https://github.com/LauraGutierrezr/CoreDeploy-LabAREP.git
cd CoreDeploy-LabAREP
mvn clean package
java -jar target/lambda-web-framework.jar
```

Then open `http://localhost:8080/` in a browser, or from another terminal:

```bash
curl "http://localhost:8080/hello?name=Ana"
# {"message": "Hello, Ana!"}

curl "http://localhost:8080/pi"
# {"value": 3.141592653589793}
```

To run with different settings:

```bash
PORT=8081 GREETING_PREFIX=Hola java -jar target/lambda-web-framework.jar
```

## Configuration (environment variables)

| Variable | Default | Effect |
|---|---|---|
| `PORT` | `8080` | Port the server listens on. |
| `GREETING_PREFIX` | `Hello` | Text prepended to the `/hello` response, e.g. `Hola` for a Spanish greeting. |
| `APP_ENV` | *(unset)* | Set to `production` to disable the `/shutdown` route before a public deployment. |
| `STATIC_FILES_PATH` | *(unset)* | If set, static files are read from this directory on disk instead of the jar's packaged classpath resources. |

`new ServerSocket(port)` binds to all network interfaces by default (not
just `127.0.0.1`), so the same jar is reachable from the cloud platform's
public IP without any extra binding configuration.

## Using the framework in a new application

A minimal application only needs the framework jar (or this module as a
dependency) and three calls:

```java
import co.edu.escuelaing.webframework.WebFramework;

public class MyApp {
    public static void main(String[] args) {
        WebFramework.staticfiles("/webroot"); // src/main/resources/webroot
        WebFramework.get("/square", (req, resp) -> {
            int n = Integer.parseInt(req.getValue("n", "0"));
            resp.type("application/json; charset=utf-8");
            return "{\"result\": " + (n * n) + "}";
        });
        WebFramework.start(); // reads PORT env var, defaults to 8080
    }
}
```

Routes and `staticfiles()` must be registered **before** `start()`: once
the accept loop is running there is nothing pulling requests off the
socket to notice a late change.

## Testing

The test suite (JUnit 5 / Jupiter, `src/test/java`) covers:

- `ContentTypesTest`, `RequestTest`, `RouterTest` — the small pure units,
  directly.
- `StaticFileServiceTest` — both static-file modes: serving `index.html`
  for `/`, nested files, path-traversal rejection outside the configured
  root (filesystem mode), and classpath resolution + `".."` rejection
  (classpath mode), using a real fixture at `src/test/resources/webroot-test`.
- `WebFrameworkIntegrationTest` — drives the whole server through real
  sockets using only the public API (`get`/`start`/`stop`), the same way a
  browser would: a registered route, an unregistered path (`404`), and a
  route that deliberately throws (`500`).

Run them with:

```bash
mvn test
```

Expected output (evidence to paste here after running locally):

```
[PASTE: mvn test output here, e.g. "Tests run: N, Failures: 0, Errors: 0, Skipped: 0"]
```

## Deploying to AWS EC2

**Cloud platform:** AWS (EC2).
**Public deployment URL:** `[PASTE: http://<instance-ip>:8081/ here after deploying]`

This assumes an EC2 instance already exists (Amazon Linux 2023, Corretto
17 installed) — the same instance used for the Networking Lab can be
reused, since this app runs on a different port. Adjust the host/port
below if you deploy to a new instance instead.

1. **Open the port in the security group.** Add an inbound rule: type
   *Custom TCP*, port `8081`, source `0.0.0.0/0` (or restrict it to your
   IP while testing). SSH (`22`) should already be open from your IP.
2. **Build the jar locally:**
   ```bash
   mvn clean package
   ```
3. **Deploy with the provided script:**
   ```bash
   ./deploy/deploy.sh <instance-ip-or-dns> /path/to/your-key.pem ec2-user
   ```
   This installs Java if needed, copies `target/lambda-web-framework.jar`,
   installs `deploy/lambda-web-framework.service` as a systemd service on
   port `8081` with `APP_ENV=production` (disabling `/shutdown`), and
   starts it.
4. **Verify:**
   ```bash
   curl http://<instance-ip>:8081/pi
   ```
   and open `http://<instance-ip>:8081/` in a browser.
5. **Confirm it survives logout:** close the SSH session, wait a moment,
   then repeat the `curl` from step 4 — the systemd service keeps it
   running.

If you would rather deploy by hand instead of running the script, see
`deploy/deploy.sh` — it is a short, readable sequence of `scp`/`ssh`
commands with no hidden steps.


### Example URLs (replace `<instance-ip>` with the real public IP/DNS)

| Resource | URL |
|---|---|
| Front page (static) | `http://<instance-ip>:8081/` |
| Static JS | `http://<instance-ip>:8081/app.js` |
| Static image (binary) | `http://<instance-ip>:8081/images/logo.png` |
| REST endpoint 1 | `http://<instance-ip>:8081/hello?name=Ana` |
| REST endpoint 2 | `http://<instance-ip>:8081/pi` |
| Unknown path (404 check) | `http://<instance-ip>:8081/does-not-exist` |

## Evidence

<img width="1011" height="297" alt="Captura de pantallaa la(s) 6 37 10 p m" src="https://github.com/user-attachments/assets/548ef27c-6fea-4d1b-87ab-1cd39ac75c25" />


<img width="717" height="267" alt="Captura de pantallaa(s) 6 36 44 p m" src="https://github.com/user-attachments/assets/948d5f48-59f6-43c3-9a83-83f506f43d8d" />

<img width="1264" height="859" alt="Captura de pantall la(s) 6 38 14 p m" src="https://github.com/user-attachments/assets/32160142-937c-4657-bb24-ee690ac72f5d" />

<img width="677" height="330" alt="Captura de pantalla(s) 6 38 48 p m" src="https://github.com/user-attachments/assets/f59b4d4f-f2cf-4f76-838e-06b408ebc713" />


**Local**
`mvn clean package` succeeding, with the test summary (`Tests run: N, Failures: 0, Errors: 0`).
`curl http://localhost:8080/hello?name=...` and `/pi` responses, run locally.
`curl http://localhost:8080/does-not-exist` returning `404`.
`curl http://localhost:8080/shutdown` (with `APP_ENV` unset/`development`) returning its confirmation message, and the server process exiting right after — evidence that `/shutdown` works in development.

**Cloud (AWS EC2)**

- Security group inbound rule for port `8081`.
- `systemctl status lambda-web-framework` on the instance showing `active (running)`.
-  Browser screenshot of the demo page working against the public EC2 IP.
-  `curl` output for both REST endpoints against the public IP (`/hello?name=...` and `/pi`).
-  Browser or `curl` evidence of the static image loading (`/images/logo.png`).
-  `curl http://<instance-ip>:8081/does-not-exist` returning `404`.
-  `curl -i http://<instance-ip>:8081/shutdown` returning `404` — evidence it is **not** available in production.
-  Evidence of the configured environment variables **without exposing secrets** — e.g. `sudo systemctl show lambda-web-framework -p Environment` on the instance, or a screenshot of the `Environment=` lines in `deploy/lambda-web-framework.service` (there is nothing secret in `PORT`/`APP_ENV`/`GREETING_PREFIX`, so this is safe to show as-is).

<img width="715" height="772" alt="image" src="https://github.com/user-attachments/assets/5e50ce87-63e2-43f0-80ec-3000d9ad2350" />

<img width="704" height="56" alt="image" src="https://github.com/user-attachments/assets/6abb694f-d3bd-4bc2-9123-a1869d4a189a" />


<img width="622" height="93" alt="image" src="https://github.com/user-attachments/assets/b14e0e60-b08e-4623-a8a6-7a80a65ea198" />


## Maintainability

The framework/application split is the main maintainability decision in
this project: `co.edu.escuelaing.app.Application` is the only class that
knows the words "hello" or "pi" exist, and it reaches the server through
exactly one public class (`WebFramework`) and four public methods. Every
other class in `co.edu.escuelaing.webframework` is package-private, so
nothing outside the framework can depend on `Router`'s internal map shape,
`HttpServer`'s socket-handling details, or how `StaticFileService` decides
between classpath and filesystem mode. Those can all change — add
wildcard routes, switch to NIO, add a cache — without touching
`Application.java` or breaking any other app built on the same framework.


## Cleanup

After grading/demo, avoid ongoing AWS charges:

```bash
ssh -i your-key.pem ec2-user@<instance-ip> sudo systemctl stop lambda-web-framework
```

then, in the AWS console: terminate the EC2 instance, release any Elastic
IP associated with it, and remove the security group rule for port `8081`
if the instance/security group is not being reused for anything else.
