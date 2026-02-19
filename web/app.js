// ========================================
// AniTail Music - Premium Landing Page JS
// ========================================

const navToggle = document.querySelector(".nav__toggle");
const navLinks = document.querySelector("[data-nav-links]");
const themeToggle = document.getElementById("themeToggle");
const languageSelect = document.getElementById("languageSelect");
const year = document.getElementById("year");
const skipLink = document.querySelector(".skip");

const STORAGE_KEYS = {
  theme: "theme",
  dynamicColor: "dynamicColor",
  language: "language"
};

const SUPPORTED_LANGUAGES = ["es", "en", "it", "fr"];
const LOCALE_BY_LANGUAGE = {
  es: "es-ES",
  en: "en-US",
  it: "it-IT",
  fr: "fr-FR"
};

const localeCache = {};
let currentMessages = {};
let currentLanguage = "es";

let isNavOpen = false;

const getNestedValue = (source, path) => {
  if (!source || !path) return undefined;

  return path.split(".").reduce((accumulator, segment) => {
    if (accumulator === null || accumulator === undefined) return undefined;

    if (Array.isArray(accumulator)) {
      const index = Number(segment);
      return Number.isInteger(index) ? accumulator[index] : undefined;
    }

    return accumulator[segment];
  }, source);
};

const t = (key, fallback = key) => {
  const primaryValue = getNestedValue(currentMessages, key);
  if (primaryValue !== undefined && primaryValue !== null) {
    return String(primaryValue);
  }

  const spanishFallback = getNestedValue(localeCache.es, key);
  if (spanishFallback !== undefined && spanishFallback !== null) {
    return String(spanishFallback);
  }

  return fallback;
};

const detectLanguage = () => {
  const storedLanguage = localStorage.getItem(STORAGE_KEYS.language);
  if (SUPPORTED_LANGUAGES.includes(storedLanguage)) {
    return storedLanguage;
  }

  const browserLanguage = (navigator.language || "es").slice(0, 2).toLowerCase();
  if (SUPPORTED_LANGUAGES.includes(browserLanguage)) {
    return browserLanguage;
  }

  return "es";
};

const loadLocale = async (language) => {
  if (localeCache[language]) {
    return localeCache[language];
  }

  const response = await fetch(`./locales/${language}.json`);
  if (!response.ok) {
    throw new Error(`Failed to load locale file: ${language}`);
  }

  const localeData = await response.json();
  localeCache[language] = localeData;
  return localeData;
};

const updateTranslatableElement = (element) => {
  if (!element || !element.dataset) return;

  if (element.dataset.i18n) {
    element.textContent = t(element.dataset.i18n);
  }

  Object.entries(element.dataset).forEach(([datasetKey, translationKey]) => {
    if (!datasetKey.startsWith("i18n") || datasetKey === "i18n") return;

    const attributeName = datasetKey
      .slice(4)
      .replace(/[A-Z]/g, (char) => `-${char.toLowerCase()}`)
      .replace(/^-/, "");

    if (!attributeName) return;

    element.setAttribute(attributeName, t(translationKey));
  });
};

const applyTranslations = () => {
  document
    .querySelectorAll("[data-i18n], [data-i18n-content], [data-i18n-alt], [data-i18n-title], [data-i18n-aria-label]")
    .forEach((element) => updateTranslatableElement(element));
};

const updateNavToggleLabel = () => {
  if (!navToggle) return;

  navToggle.setAttribute("aria-label", isNavOpen ? t("nav.closeMenu") : t("nav.openMenu"));
};

const updateThemeToggleLabel = () => {
  if (!themeToggle) return;

  const currentTheme = document.documentElement.getAttribute("data-theme") || "dark";
  themeToggle.textContent = currentTheme === "light" ? t("theme.darkMode") : t("theme.lightMode");
};

const updateCarouselLabels = () => {
  document.querySelectorAll(".carousel-btn--prev").forEach((button) => {
    button.setAttribute("aria-label", t("carousel.previousImage"));
  });

  document.querySelectorAll(".carousel-btn--next").forEach((button) => {
    button.setAttribute("aria-label", t("carousel.nextImage"));
  });
};

const setLanguage = async (language, { persist = true, refreshRelease = true } = {}) => {
  const safeLanguage = SUPPORTED_LANGUAGES.includes(language) ? language : "es";

  try {
    if (!localeCache.es) {
      await loadLocale("es");
    }

    currentMessages = await loadLocale(safeLanguage);
    currentLanguage = safeLanguage;
  } catch (error) {
    console.warn("Error loading requested locale, falling back to Spanish:", error);
    currentMessages = localeCache.es || {};
    currentLanguage = "es";
  }

  if (persist) {
    localStorage.setItem(STORAGE_KEYS.language, currentLanguage);
  }

  document.documentElement.lang = currentLanguage;

  if (languageSelect) {
    languageSelect.value = currentLanguage;
  }

  applyTranslations();
  updateThemeToggleLabel();
  updateNavToggleLabel();
  updateCarouselLabels();

  if (refreshRelease) {
    refreshReleaseWidgets();
  }
};

const initLanguage = async () => {
  await setLanguage(detectLanguage(), { persist: false, refreshRelease: false });

  languageSelect?.addEventListener("change", async (event) => {
    await setLanguage(event.target.value, { persist: true, refreshRelease: true });
  });
};

// ========================================
// Theme Management
// ========================================
const setTheme = (theme) => {
  document.documentElement.setAttribute("data-theme", theme);
  themeToggle?.setAttribute("aria-pressed", String(theme === "dark"));
  localStorage.setItem(STORAGE_KEYS.theme, theme);
  updateThemeToggleLabel();

  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) {
    meta.setAttribute("content", theme === "light" ? "#faf6f8" : "#0d090c");
  }
};

const initTheme = () => {
  const storedTheme = localStorage.getItem(STORAGE_KEYS.theme);
  if (storedTheme) {
    setTheme(storedTheme);
    return;
  }

  const prefersLight = window.matchMedia?.("(prefers-color-scheme: light)").matches;
  setTheme(prefersLight ? "light" : "dark");
};

themeToggle?.addEventListener("click", () => {
  const currentTheme = document.documentElement.getAttribute("data-theme");
  setTheme(currentTheme === "light" ? "dark" : "light");
});

// ========================================
// Mobile Navigation
// ========================================
const openNav = () => {
  if (!navLinks || !navToggle) return;

  isNavOpen = true;
  navLinks.classList.add("is-open");
  navToggle.setAttribute("aria-expanded", "true");
  document.body.style.overflow = "hidden";
  updateNavToggleLabel();

  const firstLink = navLinks.querySelector("a, button, select");
  firstLink?.focus();
};

const closeNav = () => {
  if (!navLinks || !navToggle) return;

  isNavOpen = false;
  navLinks.classList.remove("is-open");
  navToggle.setAttribute("aria-expanded", "false");
  document.body.style.overflow = "";
  updateNavToggleLabel();
};

navToggle?.addEventListener("click", () => {
  if (isNavOpen) {
    closeNav();
  } else {
    openNav();
  }
});

window.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && isNavOpen) {
    closeNav();
    navToggle?.focus();
  }
});

navLinks?.addEventListener("click", (event) => {
  if (event.target.tagName === "A") {
    closeNav();
  }
});

document.addEventListener("click", (event) => {
  if (!isNavOpen || !navLinks || !navToggle) return;

  if (!navLinks.contains(event.target) && !navToggle.contains(event.target)) {
    closeNav();
  }
});

// ========================================
// Skip Link (Accessibility)
// ========================================
skipLink?.addEventListener("click", (event) => {
  const targetId = skipLink.getAttribute("href")?.slice(1);
  const target = targetId ? document.getElementById(targetId) : null;

  if (!target) return;

  event.preventDefault();
  target.setAttribute("tabindex", "-1");
  target.focus();
  window.scrollTo({ top: target.offsetTop - 10, behavior: "smooth" });
  target.addEventListener("blur", () => target.removeAttribute("tabindex"), { once: true });
});

// ========================================
// Footer Year
// ========================================
if (year) {
  year.textContent = String(new Date().getFullYear());
}

// ========================================
// Scroll Reveal Animations
// ========================================
const initScrollAnimations = () => {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        }
      });
    },
    {
      root: null,
      rootMargin: "0px 0px -80px 0px",
      threshold: 0.1
    }
  );

  document.querySelectorAll(".reveal-on-scroll, .reveal-group").forEach((element) => {
    observer.observe(element);
  });
};

// ========================================
// Carousel
// ========================================
const initCarousel = () => {
  document.querySelectorAll("[data-carousel]").forEach((wrapper) => {
    const gallery = wrapper.querySelector(".screens__gallery");
    if (!gallery) return;

    let previousButton = wrapper.querySelector(".carousel-btn--prev");
    let nextButton = wrapper.querySelector(".carousel-btn--next");

    if (!previousButton || !nextButton) {
      previousButton = document.createElement("button");
      previousButton.className = "carousel-btn carousel-btn--prev";
      previousButton.innerHTML =
        '<svg viewBox="0 0 24 24"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>';

      nextButton = document.createElement("button");
      nextButton.className = "carousel-btn carousel-btn--next";
      nextButton.innerHTML =
        '<svg viewBox="0 0 24 24"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg>';

      wrapper.appendChild(previousButton);
      wrapper.appendChild(nextButton);
    }

    previousButton.setAttribute("aria-label", t("carousel.previousImage"));
    nextButton.setAttribute("aria-label", t("carousel.nextImage"));

    const scrollAmount = () => {
      const item = gallery.querySelector("figure");
      return item ? item.offsetWidth + 24 : 300;
    };

    previousButton.addEventListener("click", () => {
      gallery.scrollBy({ left: -scrollAmount(), behavior: "smooth" });
    });

    nextButton.addEventListener("click", () => {
      gallery.scrollBy({ left: scrollAmount(), behavior: "smooth" });
    });

    gallery.addEventListener("keydown", (event) => {
      if (event.key === "ArrowLeft") {
        gallery.scrollBy({ left: -scrollAmount(), behavior: "smooth" });
      } else if (event.key === "ArrowRight") {
        gallery.scrollBy({ left: scrollAmount(), behavior: "smooth" });
      }
    });
  });
};

// ========================================
// Smooth Scroll for Anchor Links
// ========================================
const initSmoothScroll = () => {
  document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
    anchor.addEventListener("click", function onAnchorClick(event) {
      const targetId = this.getAttribute("href");
      if (targetId === "#") return;

      const target = document.querySelector(targetId);
      if (!target) return;

      event.preventDefault();
      const headerOffset = 80;
      const elementPosition = target.getBoundingClientRect().top;
      const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

      window.scrollTo({ top: offsetPosition, behavior: "smooth" });
    });
  });
};

// ========================================
// Keyboard Focus Detection
// ========================================
const initFocusDetection = () => {
  document.addEventListener("mousedown", () => {
    document.body.classList.remove("user-is-tabbing");
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Tab") {
      document.body.classList.add("user-is-tabbing");
    }
  });
};

// ========================================
// Header Scroll Effect
// ========================================
const initHeaderScroll = () => {
  const header = document.querySelector(".site-header");
  if (!header) return;

  let isTicking = false;

  const updateHeader = () => {
    header.style.boxShadow = window.scrollY > 50 ? "0 4px 20px rgba(0, 0, 0, 0.3)" : "none";
    isTicking = false;
  };

  window.addEventListener(
    "scroll",
    () => {
      if (isTicking) return;

      requestAnimationFrame(updateHeader);
      isTicking = true;
    },
    { passive: true }
  );
};

// ========================================
// Dynamic Color Picker
// ========================================
const initColorPicker = () => {
  const colorButtons = document.querySelectorAll(".color-picker__btn");
  const html = document.documentElement;

  const storedColor = localStorage.getItem(STORAGE_KEYS.dynamicColor) || "rose";
  html.setAttribute("data-color", storedColor);

  const updateActiveButton = (color) => {
    colorButtons.forEach((button) => {
      button.classList.toggle("is-active", button.dataset.color === color);
    });
  };

  updateActiveButton(storedColor);

  colorButtons.forEach((button) => {
    button.addEventListener("click", () => {
      const color = button.dataset.color;
      if (!color) return;

      html.setAttribute("data-color", color);
      localStorage.setItem(STORAGE_KEYS.dynamicColor, color);
      updateActiveButton(color);

      button.style.transform = "scale(0.95)";
      setTimeout(() => {
        button.style.transform = "";
      }, 150);
    });
  });
};

// ========================================
// Service Worker Registration
// ========================================
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker
      .register("./sw.js")
      .then((registration) => {
        console.log("Service Worker registered:", registration.scope);
      })
      .catch((error) => {
        console.warn("Service Worker registration failed:", error);
      });
  });
}

// ========================================
// GitHub Releases
// ========================================
const escapeHtml = (value) =>
  String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#39;");

const safeExternalUrl = (url) => (/^https?:\/\//i.test(url) ? url : "#");

const parseMarkdown = (markdownText) => {
  const escaped = escapeHtml(markdownText || "");

  return escaped
    .replace(/^\s*---\s*$/gim, "")
    .replace(/^### (.*$)/gim, "<h3>$1</h3>")
    .replace(/^## (.*$)/gim, "<h2>$1</h2>")
    .replace(/^# (.*$)/gim, "<h1>$1</h1>")
    .replace(/\*\*([^*]+)\*\*/gim, "<strong>$1</strong>")
    .replace(/^\s*-\s(.*$)/gim, "<li>$1</li>")
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label, rawUrl) => {
      const href = safeExternalUrl(rawUrl);
      return `<a href="${href}" target="_blank" rel="noopener noreferrer">${label}</a>`;
    })
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\n/gim, "<br>");
};

const releaseDateLocale = () => LOCALE_BY_LANGUAGE[currentLanguage] || "en-US";
const RELEASE_WIDGETS = [
  {
    key: "android",
    containerId: "github-release",
    repository: "Animetailapp/AniTail",
    fallbackUrl: "https://github.com/Animetailapp/AniTail/releases",
    localeRoot: "downloads"
  },
  {
    key: "desktop",
    containerId: "github-release-desktop",
    repository: "Animetailapp/Anitail-Desktop",
    fallbackUrl: "https://github.com/Animetailapp/Anitail-Desktop/releases",
    localeRoot: "desktopDownloads"
  }
];

const releaseStateMap = {};

const getReleaseState = (widgetKey) => {
  if (!releaseStateMap[widgetKey]) {
    releaseStateMap[widgetKey] = {
      releases: [],
      selectedReleaseId: null,
      hasError: false,
      isBound: false
    };
  }

  return releaseStateMap[widgetKey];
};

const formatAssetTypeLabel = (widget, lowerAssetName) => {
  if (widget.key === "android") {
    if (lowerAssetName.includes("universal")) return t("downloads.assetUniversal");
    if (lowerAssetName.includes("arm64")) return t("downloads.assetArm64");
    return t("downloads.assetApk");
  }

  if (lowerAssetName.endsWith(".exe") || lowerAssetName.endsWith(".msi") || lowerAssetName.includes("win")) {
    return t("desktopDownloads.assetWindows");
  }
  if (lowerAssetName.endsWith(".dmg") || lowerAssetName.endsWith(".pkg") || lowerAssetName.includes("mac")) {
    return t("desktopDownloads.assetMac");
  }
  if (
    lowerAssetName.endsWith(".appimage") ||
    lowerAssetName.endsWith(".deb") ||
    lowerAssetName.endsWith(".rpm") ||
    lowerAssetName.endsWith(".tar.gz") ||
    lowerAssetName.includes("linux")
  ) {
    return t("desktopDownloads.assetLinux");
  }

  return t("desktopDownloads.assetDesktop");
};

const isAllowedReleaseAsset = (widget, lowerAssetName) => {
  if (widget.key === "android") {
    return lowerAssetName.endsWith(".apk");
  }

  return (
    lowerAssetName.endsWith(".exe") ||
    lowerAssetName.endsWith(".msi") ||
    lowerAssetName.endsWith(".dmg") ||
    lowerAssetName.endsWith(".pkg") ||
    lowerAssetName.endsWith(".appimage") ||
    lowerAssetName.endsWith(".deb") ||
    lowerAssetName.endsWith(".rpm") ||
    lowerAssetName.endsWith(".zip") ||
    lowerAssetName.endsWith(".tar.gz")
  );
};

const renderReleaseLoading = (widget) => {
  const container = document.getElementById(widget.containerId);
  if (!container) return;

  container.innerHTML = `
    <div class="release-loading">
      <div class="spinner"></div>
      <p>${escapeHtml(t(`${widget.localeRoot}.loading`))}</p>
    </div>
  `;
};

const renderReleaseError = (widget) => {
  const container = document.getElementById(widget.containerId);
  if (!container) return;

  container.innerHTML = `
    <div class="release-loading">
      <p>${escapeHtml(t(`${widget.localeRoot}.loadingError`))}</p>
      <a href="${safeExternalUrl(widget.fallbackUrl)}" class="button" target="_blank" rel="noopener noreferrer">${escapeHtml(
        t(`${widget.localeRoot}.openGithub`)
      )}</a>
    </div>
  `;
};

const renderReleaseForWidget = (widget, releaseData) => {
  const container = document.getElementById(widget.containerId);
  if (!container || !releaseData) return;

  const state = getReleaseState(widget.key);
  state.selectedReleaseId = releaseData.id;

  const safeTagName = encodeURIComponent(releaseData.tag_name || "-");
  const publishedDate = new Date(releaseData.published_at).toLocaleDateString(releaseDateLocale(), {
    year: "numeric",
    month: "long",
    day: "numeric"
  });
  const versionSelectId = `${widget.key}-version-select`;

  const selectorHtml = `
    <div class="release-selector-wrapper">
      <label for="${versionSelectId}">${escapeHtml(t(`${widget.localeRoot}.versionLabel`))}</label>
      <select id="${versionSelectId}" class="version-select">
        ${state.releases
          .map((release, index) => {
            const latestSuffix = index === 0 ? ` (${escapeHtml(t(`${widget.localeRoot}.latestLabel`))})` : "";
            const optionLabel = `${escapeHtml(release.tag_name)}${latestSuffix}`;
            const selectedAttribute = release.id === releaseData.id ? "selected" : "";
            return `<option value="${release.id}" ${selectedAttribute}>${optionLabel}</option>`;
          })
          .join("")}
      </select>
    </div>
  `;

  const badges = `
    <div class="release-header">
      ${selectorHtml}
      <div class="release-badges-row">
        <img src="https://img.shields.io/badge/release-${safeTagName}-007ec6?style=for-the-badge&logo=github" alt="Version" class="release-badge">
        <img src="https://img.shields.io/github/downloads/${widget.repository}/total?style=for-the-badge&color=44cc11&label=Downloads" alt="Downloads" class="release-badge">
        <img src="https://img.shields.io/github/license/${widget.repository}?style=for-the-badge&color=fa751e" alt="License" class="release-badge">
      </div>
      <span class="release-meta">${escapeHtml(t(`${widget.localeRoot}.publishedOn`))} ${escapeHtml(publishedDate)}</span>
    </div>
  `;

  const releaseAssets = (releaseData.assets || [])
    .filter((asset) => isAllowedReleaseAsset(widget, asset.name.toLowerCase()))
    .map((asset) => {
      const safeName = escapeHtml(asset.name);
      const size = `${(asset.size / 1024 / 1024).toFixed(1)} MB`;
      const type = escapeHtml(formatAssetTypeLabel(widget, asset.name.toLowerCase()));
      const href = safeExternalUrl(asset.browser_download_url);

      return `
        <a href="${href}" class="asset-btn" target="_blank" rel="noopener noreferrer">
           <div class="asset-info">
             <span class="asset-name">${escapeHtml(t(`${widget.localeRoot}.downloadPrefix`))} ${type}</span>
             <span class="asset-size">${safeName} (${size})</span>
           </div>
           <svg class="asset-icon" width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </a>
      `;
    })
    .join("");

  const extraAndroidAsset =
    widget.key === "android"
      ? `
        <a href="https://apt.izzysoft.de/fdroid/index/apk/anitail.music" class="asset-btn" target="_blank" rel="noopener noreferrer">
           <div class="asset-info">
             <span class="asset-name">${escapeHtml(t("downloads.fdroidName"))}</span>
             <span class="asset-size">${escapeHtml(t("downloads.fdroidSource"))}</span>
           </div>
           <svg class="asset-icon" width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </a>
      `
      : "";

  container.innerHTML = `
    ${badges}
    <div class="release-content">
      <div class="release-notes markdown-body">${parseMarkdown(releaseData.body || "")}</div>
      <h4>${escapeHtml(t(`${widget.localeRoot}.installFiles`))}</h4>
      <div class="release-assets">
        ${releaseAssets}
        ${extraAndroidAsset}
      </div>
    </div>
  `;
};

const renderSelectedReleaseForWidget = (widget) => {
  const state = getReleaseState(widget.key);
  if (!state.releases.length) return;

  const selectedRelease = state.releases.find((release) => String(release.id) === String(state.selectedReleaseId));
  if (selectedRelease) {
    renderReleaseForWidget(widget, selectedRelease);
    return;
  }

  state.selectedReleaseId = state.releases[0].id;
  renderReleaseForWidget(widget, state.releases[0]);
};

const refreshReleaseWidgets = () => {
  RELEASE_WIDGETS.forEach((widget) => {
    const container = document.getElementById(widget.containerId);
    if (!container) return;

    const state = getReleaseState(widget.key);
    if (state.releases.length > 0) {
      renderSelectedReleaseForWidget(widget);
      return;
    }

    if (state.hasError) {
      renderReleaseError(widget);
      return;
    }

    renderReleaseLoading(widget);
  });
};

const initReleaseWidget = async (widget) => {
  const container = document.getElementById(widget.containerId);
  if (!container) return;

  const state = getReleaseState(widget.key);
  renderReleaseLoading(widget);

  if (!state.isBound) {
    container.addEventListener("change", (event) => {
      if (!event.target.matches(".version-select")) return;

      state.selectedReleaseId = event.target.value;
      renderSelectedReleaseForWidget(widget);
    });
    state.isBound = true;
  }

  try {
    const response = await fetch(`https://api.github.com/repos/${widget.repository}/releases`);
    if (!response.ok) {
      throw new Error(`Failed to fetch releases for ${widget.repository}`);
    }

    const releases = await response.json();
    state.releases = Array.isArray(releases) ? releases : [];

    if (!state.releases.length) {
      throw new Error(`No releases found for ${widget.repository}`);
    }

    state.selectedReleaseId = state.releases[0].id;
    state.hasError = false;
    renderReleaseForWidget(widget, state.releases[0]);
  } catch (error) {
    console.error(error);
    state.hasError = true;
    renderReleaseError(widget);
  }
};

const initGitHubReleases = async () => {
  await Promise.all(RELEASE_WIDGETS.map((widget) => initReleaseWidget(widget)));
};

// ========================================
// Initialize Everything
// ========================================
document.addEventListener("DOMContentLoaded", async () => {
  await initLanguage();
  initTheme();
  initColorPicker();
  initScrollAnimations();
  initCarousel();
  initSmoothScroll();
  initFocusDetection();
  initHeaderScroll();
  initGitHubReleases();
});
