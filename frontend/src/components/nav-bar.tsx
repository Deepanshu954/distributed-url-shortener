import { Link, useLocation } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Link2, LayoutDashboard, PlusCircle, Activity } from 'lucide-react';

export function NavBar() {
  const location = useLocation();

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border/40 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container flex h-16 items-center justify-between px-4 sm:px-8 max-w-6xl mx-auto">
        <Link to="/" className="flex items-center gap-2.5 font-extrabold text-xl tracking-tight transition-opacity hover:opacity-90">
          <div className="h-9 w-9 rounded-xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-pink-500 flex items-center justify-center text-white shadow-md shadow-indigo-500/20">
            <Link2 className="h-5 w-5" />
          </div>
          <div className="flex flex-col">
            <span className="bg-gradient-to-r from-foreground to-muted-foreground bg-clip-text text-transparent">
              TinyScale
            </span>
            <span className="text-[10px] text-muted-foreground font-mono uppercase tracking-wider -mt-1">
              Distributed Engine
            </span>
          </div>
        </Link>

        <nav className="flex items-center gap-1 sm:gap-2">
          <Button
            variant={location.pathname === '/' ? 'secondary' : 'ghost'}
            size="sm"
            asChild
            className="text-sm font-medium"
          >
            <Link to="/">
              <PlusCircle className="mr-1.5 h-4 w-4" />
              Shorten
            </Link>
          </Button>

          <Button
            variant={location.pathname === '/dashboard' ? 'secondary' : 'ghost'}
            size="sm"
            asChild
            className="text-sm font-medium"
          >
            <Link to="/dashboard">
              <LayoutDashboard className="mr-1.5 h-4 w-4" />
              Dashboard
            </Link>
          </Button>

          <div className="h-4 w-px bg-border mx-1 hidden sm:block" />

          <div className="hidden sm:flex items-center gap-1.5 text-xs text-emerald-500 font-medium px-2 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20">
            <Activity className="h-3 w-3 animate-pulse" />
            <span>Active Ring</span>
          </div>

          <Button variant="ghost" size="icon" asChild className="text-muted-foreground hover:text-foreground">
            <a
              href="https://github.com/Deepanshu9548/distributed-url-shortener-v2"
              target="_blank"
              rel="noreferrer"
              title="GitHub Repository"
            >
              <svg className="h-4 w-4 fill-current" viewBox="0 0 24 24" aria-hidden="true">
                <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
              </svg>
            </a>
          </Button>
        </nav>
      </div>
    </header>
  );
}
