// Asynchronous browser client for the demo. Both calls use fetch() and
// never reload the page: a network failure (server unreachable) and an
// HTTP-level error response are reported differently, so the two failure
// modes are never confused with each other.

const helloForm = document.getElementById("hello-form");
const nameInput = document.getElementById("name-input");
const helloResult = document.getElementById("hello-result");

const piButton = document.getElementById("pi-button");
const piResult = document.getElementById("pi-result");

function showResult(target, text, isError) {
  target.textContent = text;
  target.classList.toggle("error", Boolean(isError));
}

async function callJson(path, target) {
  showResult(target, "Loading...", false);
  try {
    const response = await fetch(path);
    let body;
    try {
      body = await response.json();
    } catch (parseError) {
      body = { raw: await response.text().catch(() => "") };
    }
    if (!response.ok) {
      showResult(target, `HTTP ${response.status}: ${JSON.stringify(body)}`, true);
      return;
    }
    showResult(target, JSON.stringify(body, null, 2), false);
  } catch (networkError) {
    showResult(target, `Network error: ${networkError.message}`, true);
  }
}

helloForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const name = nameInput.value.trim();
  const query = name ? `?name=${encodeURIComponent(name)}` : "";
  callJson(`/hello${query}`, helloResult);
});

piButton.addEventListener("click", () => {
  callJson("/pi", piResult);
});
