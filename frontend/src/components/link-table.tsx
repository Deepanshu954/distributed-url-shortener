import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useLinks, useDeleteLink, getLocalLinks, type LinkItem } from '@/hooks/use-links';
import { useLinkStats } from '@/hooks/use-stats';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Copy, Trash2, ExternalLink, Check, BarChart3 } from 'lucide-react';
import { toast } from 'sonner';

function LinkRow({ item, isLocal }: { item: LinkItem; isLocal?: boolean }) {
  const { data: stats } = useLinkStats(item.shortCode);
  const deleteMutation = useDeleteLink();
  const [copied, setCopied] = useState(false);

  const getFullShortUrl = (code: string) => {
    const origin = window.location.origin;
    const pathname = window.location.pathname.endsWith('/')
      ? window.location.pathname
      : `${window.location.pathname}/`;
    return `${origin}${pathname}#/${code}`;
  };

  const handleCopy = () => {
    const shortUrl = getFullShortUrl(item.shortCode);
    navigator.clipboard.writeText(shortUrl).then(
      () => {
        setCopied(true);
        toast.success('Copied to clipboard');
        setTimeout(() => setCopied(false), 2000);
      },
      () => toast.error('Failed to copy')
    );
  };

  const handleDelete = () => {
    if (confirm(`Are you sure you want to delete /${item.shortCode}?`)) {
      deleteMutation.mutate(item.shortCode, {
        onSuccess: () => toast.success('Link deleted successfully'),
        onError: () => toast.error('Failed to delete link'),
      });
    }
  };

  return (
    <TableRow className="hover:bg-muted/50 transition-colors">
      <TableCell className="font-medium">
        <div className="flex items-center gap-2">
          <span className="font-mono text-primary font-bold">{item.shortCode}</span>
          {isLocal && (
            <span className="text-[10px] bg-indigo-500/10 text-indigo-500 font-semibold px-1.5 py-0.5 rounded border border-indigo-500/20">
              Mine
            </span>
          )}
          <Button variant="ghost" size="icon" className="h-6 w-6 text-muted-foreground hover:text-foreground" onClick={handleCopy}>
            {copied ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3 w-3" />}
          </Button>
        </div>
      </TableCell>

      <TableCell className="max-w-[280px] sm:max-w-md truncate" title={item.longUrl}>
        <a
          href={item.longUrl}
          target="_blank"
          rel="noreferrer"
          className="flex items-center gap-1.5 hover:underline text-foreground text-sm truncate"
        >
          <span className="truncate">{item.longUrl}</span>
          <ExternalLink className="h-3 w-3 shrink-0 text-muted-foreground" />
        </a>
      </TableCell>

      <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
        {item.createdAt ? new Intl.DateTimeFormat('default', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(item.createdAt)) : '-'}
      </TableCell>

      <TableCell>
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-primary/10 text-primary">
          {stats ? `${stats.clickCount} clicks` : <Skeleton className="h-4 w-12" />}
        </span>
      </TableCell>

      <TableCell className="text-right space-x-1.5 whitespace-nowrap">
        <Button variant="outline" size="sm" asChild className="h-8 px-2 text-xs">
          <Link to={`/links/${item.shortCode}`}>
            <BarChart3 className="h-3.5 w-3.5 mr-1" /> Stats
          </Link>
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={handleDelete}
          disabled={deleteMutation.isPending}
          className="h-8 px-2 text-destructive hover:text-destructive hover:bg-destructive/10"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </Button>
      </TableCell>
    </TableRow>
  );
}

export function LinkTable() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useLinks(page, 20);
  const localLinks = getLocalLinks();

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-10 w-full rounded-md" />
        <Skeleton className="h-16 w-full rounded-md" />
        <Skeleton className="h-16 w-full rounded-md" />
      </div>
    );
  }

  // Combine server items and local links without duplicate short codes
  const serverItems = data?.content || [];
  const localCodes = new Set(localLinks.map((l) => l.shortCode));
  const combinedItems = [
    ...localLinks,
    ...serverItems.filter((s) => !localCodes.has(s.shortCode)),
  ];

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-border/80 bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader className="bg-muted/40">
            <TableRow>
              <TableHead className="w-[180px]">Short Code</TableHead>
              <TableHead>Destination URL</TableHead>
              <TableHead className="w-[140px]">Created</TableHead>
              <TableHead className="w-[100px]">Activity</TableHead>
              <TableHead className="text-right w-[160px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {combinedItems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground h-32">
                  <div className="flex flex-col items-center justify-center gap-1.5">
                    <p className="text-sm font-medium">No links shortened yet.</p>
                    <Link to="/" className="text-xs text-primary underline">
                      Shorten your first URL
                    </Link>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              combinedItems.map((item) => (
                <LinkRow key={item.shortCode} item={item} isLocal={localCodes.has(item.shortCode)} />
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between pt-2">
          <span className="text-xs text-muted-foreground">
            Page {data.number + 1} of {data.totalPages}
          </span>
          <div className="space-x-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={data.number === 0}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
              disabled={data.number >= data.totalPages - 1}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
