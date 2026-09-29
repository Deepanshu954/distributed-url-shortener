import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { NavBar } from '@/components/nav-bar';
import { Toaster } from '@/components/ui/sonner';

import Landing from '@/pages/landing';
import Dashboard from '@/pages/dashboard';
import LinkDetail from '@/pages/link-detail';
import RedirectHandler from '@/pages/redirect-handler';
import NotFound from '@/pages/not-found';

import { ErrorBoundary } from '@/components/error-boundary';

export default function App() {
  return (
    <ErrorBoundary>
      <HashRouter>
        <div className="min-h-screen flex flex-col bg-background font-sans antialiased selection:bg-indigo-500 selection:text-white">
          <NavBar />
          <main className="flex-1">
            <Routes>
              <Route path="/" element={<Landing />} />
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/links/new" element={<Navigate to="/" replace />} />
              <Route path="/links/:code" element={<LinkDetail />} />
              <Route path="/:code" element={<RedirectHandler />} />
              <Route path="*" element={<NotFound />} />
            </Routes>
          </main>
          <footer className="py-6 border-t border-border/40 mt-auto text-center text-sm text-muted-foreground bg-muted/20">
            <div className="container mx-auto px-4">
              <p className="flex items-center justify-center gap-2 flex-wrap text-xs">
                <span>TinyScale Distributed Engine</span>
                <span>•</span>
                <span>Snowflake 64-bit Sequencer</span>
                <span>•</span>
                <span>Murmur3 Consistent Hash Ring</span>
                <span>•</span>
                <a
                  href="https://github.com/Deepanshu9548/distributed-url-shortener-v2"
                  className="font-medium underline hover:text-foreground"
                  target="_blank"
                  rel="noreferrer"
                >
                  GitHub
                </a>
              </p>
            </div>
          </footer>
          <Toaster position="top-right" richColors />
        </div>
      </HashRouter>
    </ErrorBoundary>
  );
}
