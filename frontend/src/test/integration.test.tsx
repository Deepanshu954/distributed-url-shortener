import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider, QueryClient } from '@tanstack/react-query';
import App from '@/App';

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

const renderWithProviders = (ui: React.ReactElement, { route = '/' } = {}) => {
  window.location.hash = '#' + route;
  return render(
    <QueryClientProvider client={createTestQueryClient()}>{ui}</QueryClientProvider>
  );
};

describe('Frontend Integration Tests', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('Landing page shortens URL and renders result card', async () => {
    renderWithProviders(<App />, { route: '/' });

    const user = userEvent.setup();
    const input = screen.getByPlaceholderText(/Paste your long link here/i);
    await user.type(input, 'https://github.com/torvalds/linux');

    const shortenBtn = screen.getByRole('button', { name: /^Shorten/i });
    await user.click(shortenBtn);

    await waitFor(() => {
      expect(screen.getByText(/Link Shortened Successfully!/i)).toBeInTheDocument();
    });

    expect(screen.getAllByText(/newlink/i).length).toBeGreaterThan(0);
  });

  it('Dashboard renders links table and click activity', async () => {
    renderWithProviders(<App />, { route: '/dashboard' });

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Dashboard/i })).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('link1')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText(/10 clicks/i)).toBeInTheDocument();
    });
  });

  it('Landing page enforces URL validation', async () => {
    renderWithProviders(<App />, { route: '/' });
    const user = userEvent.setup();

    const shortenBtn = screen.getByRole('button', { name: /^Shorten/i });
    await user.click(shortenBtn);

    await waitFor(() => {
      expect(screen.getByText(/Please enter a web address to shorten/i)).toBeInTheDocument();
    });
  });

  it('Link details page renders configuration and analytics chart', async () => {
    renderWithProviders(<App />, { route: '/links/link1' });

    await waitFor(() => {
      expect(screen.getByText(/Link Configuration/i)).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText(/Click Performance/i)).toBeInTheDocument();
      expect(screen.getByText(/10/i)).toBeInTheDocument();
    });
  });

  it('Landing page handles rate limit (429) errors gracefully', async () => {
    renderWithProviders(<App />, { route: '/' });
    const user = userEvent.setup();

    const input = screen.getByPlaceholderText(/Paste your long link here/i);
    await user.type(input, 'https://ratelimit.com');

    const shortenBtn = screen.getByRole('button', { name: /^Shorten/i });
    await user.click(shortenBtn);

    await waitFor(() => {
      expect(screen.getByText(/Too many requests/i)).toBeInTheDocument();
    });
  });

  it('Landing page supports custom alias shortening', async () => {
    renderWithProviders(<App />, { route: '/' });
    const user = userEvent.setup();

    const input = screen.getByPlaceholderText(/Paste your long link here/i);
    await user.type(input, 'https://example.com/custom');

    // Click customize alias toggle button
    const customizeBtn = screen.getByRole('button', { name: /Custom alias & expiration options/i });
    await user.click(customizeBtn);

    const aliasInput = screen.getByPlaceholderText(/my-project-v2/i);
    await user.type(aliasInput, 'my-brand');

    const shortenBtn = screen.getByRole('button', { name: /^Shorten/i });
    await user.click(shortenBtn);

    await waitFor(() => {
      expect(screen.getByText(/Link Shortened Successfully!/i)).toBeInTheDocument();
    });

    expect(screen.getAllByText(/my-brand/i).length).toBeGreaterThan(0);
  });
});

