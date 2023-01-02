const Base64 = {
  encode: function(str) {
    return btoa(unescape(encodeURIComponent(str)));
  },

  decode: function(str) {
    return decodeURIComponent(escape(window.atob(str)));
  }
};

const setErrorDisplay = display => {
  const el = document.querySelector(".event-source-error");

  if (typeof el !== "undefined") {
    el.style.display = display;
  }
}

if (window.location.pathname === "/") {
  const eventSource = new EventSource("/event-source");

  eventSource.onopen = (event) => {
    console.debug(event);
    setErrorDisplay("none");
  };

  eventSource.onmessage = (event) => {
    console.debug(event);
    document.querySelector("main").outerHTML = Base64.decode(event.data);
    document.dispatchEvent(new Event("DOMContentLoaded"));
  };

  eventSource.onerror = (event) => {
    console.error(event);
    window.setTimeout(() => setErrorDisplay("flex"), 1000);
  }
}

const toggleLevel = (el, newState) => {
  const headerCells = el.querySelectorAll(":scope > thead > tr > th:not([data-action]):not(.count)");
  const tbody = el.querySelectorAll(":scope > tbody");

  headerCells.forEach(el => {
    if (newState == "expanded") {
      el.style.display = "table-cell"
    } else {
      el.style.display = "none";
    }
  });

  tbody.forEach(el => {
    if (newState == "expanded") {
      el.style.display = "table-row-group"
    } else {
      el.style.display = "none";
    }
  });

  const toggle = el.querySelector(":scope > thead > tr > [data-action = toggle-level]");

  if (newState == "expanded") {
    toggle.textContent = "－";
  } else {
    toggle.textContent = "＋";
  }

  el.dataset.state = newState;
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

  target.querySelectorAll("[data-action = toggle-length]").forEach(initToggleLength);
}

const otherState = state => {
  if (state === "expanded") {
    return "collapsed";
  } else {
    return "expanded"
  }
}

const determineNewState = (collapsers) => {
  const states = Array.from(collapsers).map(el => el.dataset.state || "expanded");
  const hiddenStates = states.filter(state => state === "collapsed");
  const visibleStates = states.filter(state => state === "expanded");

  if (visibleStates.length > hiddenStates.length) {
    return "collapsed";
  } else {
    return "expanded";
  }
}

const initToggleLevel = (el) => {
  el.querySelectorAll("[data-action = toggle-level]").forEach((toggle) => {
    toggle.addEventListener("click", (event) => {
      const table = event.currentTarget.closest("table");

      if (event.altKey) {
        const tbody = table.querySelector(":scope > tbody");
        const tables = tbody.querySelectorAll(":scope > tr > td > table");
        const state = determineNewState(tables);

        tables.forEach(el => toggleLevel(el, state));
      } else {
        toggleLevel(table, otherState(table.dataset.state));
      }
    });
  });
}

const initToggleLength = (el) => {
  el.addEventListener("click", (event) => {
    toggleLength(event.currentTarget, otherState(el.dataset.state));
  });
}

document.addEventListener("DOMContentLoaded", (_) => {
  document.querySelectorAll("[data-action = toggle-length]").forEach(el => {
    initToggleLength(el);
  });

  initToggleLevel(document);
});
