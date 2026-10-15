(() => {
  const storageKey = 'mtl-map-reports-theme';
  const root = document.documentElement;
  let saved;
  try {
    saved = window.localStorage.getItem(storageKey);
  } catch (error) {
    console.warn('Map report theme preference could not be read:', error.name);
  }
  const apply = dark => {
    root.classList.toggle('dark', dark);
    root.style.colorScheme = dark ? 'dark' : 'light';
  };
  apply(saved === 'dark' || (saved !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches));
  window.mtlMapReportTheme = {
    isDark: () => root.classList.contains('dark'),
    setDark: dark => {
      apply(dark);
      try {
        window.localStorage.setItem(storageKey, dark ? 'dark' : 'light');
      } catch (error) {
        console.warn('Map report theme preference could not be saved:', error.name);
      }
    }
  };
})();
