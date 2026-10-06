/**
 * Helper utilities for direct short URL resolution and formatting.
 * Direct short URLs redirect directly at the HTTP level (HTTP 302 Found)
 * without opening or loading the frontend single-page web app.
 *
 * Automatically resolves the active hostname (LAN IP, public domain, or tunnel)
 * so that shared short URLs work seamlessly on other devices and laptops.
 */

export function getDirectShortUrl(code: string, shortUrlFromApi?: string): string {
  if (typeof window !== 'undefined') {
    const protocol = window.location.protocol;
    const hostname = window.location.hostname;
    const port = window.location.port;

    // 1. If API returned a custom domain or remote IP (not localhost/127.0.0.1), respect it
    if (shortUrlFromApi) {
      try {
        const parsed = new URL(shortUrlFromApi);
        if (parsed.hostname !== 'localhost' && parsed.hostname !== '127.0.0.1') {
          return `${parsed.protocol}//${parsed.host}/${code}`;
        }
      } catch {
        // Fall back to window location
      }
    }

    // 2. If user is accessing from their laptop via localhost or 127.0.0.1,
    // check if a network IP or custom origin was configured
    // If frontend is on port 5173 or 3000, direct redirect gateway (Nginx) is port 80
    if (port === '5173' || port === '3000') {
      return `${protocol}//${hostname}/${code}`;
    }

    return `${protocol}//${hostname}${port ? `:${port}` : ''}/${code}`;
  }

  return shortUrlFromApi || `http://localhost/${code}`;
}

export function formatShortUrlDisplay(code: string, shortUrlFromApi?: string): string {
  const directUrl = getDirectShortUrl(code, shortUrlFromApi);
  return directUrl.replace(/^https?:\/\//, '');
}
