const Base64 = {
  encode: function(str) {
    return btoa(unescape(encodeURIComponent(str)));
  },

  decode: function(str) {
    return decodeURIComponent(escape(window.atob(str)));
  }
};

const timeUnits = {
  year: 24 * 60 * 60 * 1000 * 365,
  month: 24 * 60 * 60 * 1000 * 365/12,
  day: 24 * 60 * 60 * 1000,
  hour: 60 * 60 * 1000,
  minute: 60 * 1000,
  second: 1000
}

const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

const reltime = (d1, d2 = new Date()) => {
  const elapsed = d1 - d2;

  for (const [unit, millis] of Object.entries(timeUnits)) {
    if (Math.abs(elapsed) > millis || unit == 'second') {
      return rtf.format(Math.round(elapsed / millis), unit);
    }
  }
}

const eventSource = new EventSource("/event-source");

eventSource.onopen = (event) => {
  console.debug(event);
  document.querySelector(".event-source-error").classList.remove("show");
};

eventSource.addEventListener("tab", (event) => {
  console.debug(event);
  const json = JSON.parse(event.data);
  document.querySelector("main").outerHTML = Base64.decode(json.html);

  if (json.history) {
    history.pushState({}, "", `/id/${event.lastEventId}`);
  }

  document.dispatchEvent(new Event("DOMContentLoaded"));
});

eventSource.onerror = (event) => {
  console.error(event);
  window.setTimeout(() => document.querySelector(".event-source-error").classList.add("show"), 3000);
}

const getTarget = (el) => {
  if (el.dataset.target) {
    return el.closest(el.dataset.target);
  } else {
    return el;
  }
}

const toggleLength = (el, newState) => {
  const state = el.dataset.state;

  if (newState === state) return;

  const target = getTarget(el);
  const html = el.innerHTML;
  const newHTML = Base64.decode(el.getAttribute("data-value"));

  target.innerHTML = newHTML;
  el.setAttribute("data-value", Base64.encode(html));

  initToggleLevel(target);
  initToggleLength(target);
}

const flipState = state => {
  if (state === "expanded") {
    return "collapsed";
  } else {
    return "expanded"
  }
}

const determineNewState = (elements) => {
  const states = Array.from(elements).map(el => el.dataset.state || "expanded");
  const hiddenStates = states.filter(state => state === "collapsed");
  const visibleStates = states.filter(state => state === "expanded");

  if (visibleStates.length > hiddenStates.length) {
    return "collapsed";
  } else {
    return "expanded";
  }
}

const flipIcon = (el, newState) => {
  if (newState == "collapsed") {
    el.textContent = "＋";
  } else {
    el.textContent = "－";
  }
}

const bxDispatch = (el) => {
  const bxTarget = el.getAttribute('bx-target');
  const target = el.closest(el.getAttribute('bx-target'));
  const href = el.getAttribute('href');
  const method = el.getAttribute('bx-request');
  const uri = el.getAttribute('bx-uri');
  const swap = el.getAttribute('bx-swap') || "innerHTML";

  fetch(uri, {method: method, headers: {'bx-request': 'true'}}).then((response) => {
    if (response.ok) {
      response.text().then(html => {
        const parent = target.parentNode;

        if (swap == "innerHTML") {
          target.innerHTML = html
        } else if (swap == "outerHTML") {
          target.outerHTML = html;
        }

        init(parent);

        const pushUrl = el.getAttribute('bx-push-url');

        if (pushUrl && pushUrl.length > 0) {
          history.pushState({}, "", pushUrl);
        }
      });
    }
  });
}

const toggle = (el, newState) => {
  if (el.getAttribute("bx-dispatch")) {
    bxDispatch(el);
  } else {
    flipIcon(el, newState);
    el.closest("table").dataset.state = newState;
  }
}

const toggleHandler = (event) => {
  event.stopPropagation();
  event.preventDefault();

  const el = event.currentTarget;
  const table = el.closest("table");

  if (event.altKey) {
    const toggles = table.querySelectorAll(":scope > tbody > tr > * > table > thead > tr > [data-action = toggle-level]");
    const newState = determineNewState(table.querySelectorAll(":scope > tbody > tr > * > table"));

    toggles.forEach(el => { toggle(el, newState) });
  } else {
    toggle(el, flipState(table.dataset.state));
  }
}

const initToggleLevel = (root) => {
  root.querySelectorAll("[data-action = toggle-level]").forEach((toggle) => {
    toggle.addEventListener("click", toggleHandler);
  });
}

const initToggleLength = (root) => {
  root.querySelectorAll("[data-action = toggle-length]").forEach((toggle) => {
    toggle.addEventListener("click", (event) => {
      const el = event.currentTarget;
      toggleLength(el, flipState(el.dataset.state));
    });
  })
}

const initZoom = (root) => {
  root.querySelectorAll(".value-type a").forEach(el => {
    el.addEventListener("click", (event) => {
      const el = event.currentTarget;
      const table = el.closest("table");

      if (event.altKey) {
        event.preventDefault();

        fetch(`/clip/${table.id}`, {method: "post"}).then(response => {
          if (response.ok) {
            const ok = root.querySelector(".ok");
            ok.classList.add("show");

            window.setTimeout(() => {
              ok.classList.remove("show");
            }, 3000);
          }
        });
      } else if (el.getAttribute("bx-dispatch")) {
        event.preventDefault();
        bxDispatch(el);
      }
    });
  });
}

const relativize = (time) => {
  const attr = time.getAttribute("datetime");

  if (attr !== "") {
    const date = Date.parse(attr);

    if (!isNaN(date)) {
      time.textContent = reltime(date);
      time.classList.add("show");
    }
  }
}

let interval;

const init = (el) => {
  const time = document.querySelector("footer time");

  relativize(time);

  if (interval != undefined) {
    window.clearInterval(interval);
  }

  interval = window.setInterval(() => relativize(time), 5000);

  initToggleLength(el);
  initToggleLevel(el);
  initZoom(el);

  window.onpopstate = (event) => {
    location.reload();
  }
}

document.addEventListener("DOMContentLoaded", (_) => {
  init(document);
});
