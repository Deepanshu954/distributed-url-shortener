import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get('*/api/links', () => {
    return HttpResponse.json({
      content: [
        {
          shortCode: 'link1',
          shortUrl: 'http://localhost:8080/link1',
          longUrl: 'https://example.com/1',
          createdAt: '2023-01-01T00:00:00Z',
          expiresAt: null,
        },
      ],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
  }),

  http.post('*/api/links', async ({ request }) => {
    const body = (await request.json()) as { longUrl: string; customAlias?: string };
    if (body.longUrl === 'https://ratelimit.com') {
      return HttpResponse.json(
        { error: 'Too many requests' },
        { status: 429, headers: { 'Retry-After': '5' } }
      );
    }
    const shortCode = body.customAlias || 'newlink';
    return HttpResponse.json(
      {
        shortCode,
        shortUrl: `http://localhost:8080/${shortCode}`,
        longUrl: body.longUrl,
        createdAt: '2026-01-01T00:00:00Z',
        expiresAt: null,
      },
      { status: 201 }
    );
  }),

  http.get('*/api/links/:code', ({ params }) => {
    if (params.code === 'unknown') {
      return HttpResponse.json({ error: 'Not found' }, { status: 404 });
    }
    return HttpResponse.json({
      shortCode: params.code,
      shortUrl: `http://localhost:8080/${params.code}`,
      longUrl: 'https://example.com',
      createdAt: '2023-01-01T00:00:00Z',
      expiresAt: null,
    });
  }),

  http.put('*/api/links/:code', async ({ params, request }) => {
    const body = (await request.json()) as { longUrl?: string };
    return HttpResponse.json({
      shortCode: params.code,
      shortUrl: `http://localhost:8080/${params.code}`,
      longUrl: body.longUrl || 'https://updated.com',
      createdAt: '2023-01-01T00:00:00Z',
      expiresAt: null,
    });
  }),

  http.delete('*/api/links/:code', () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('*/api/links/:code/stats', ({ params }) => {
    return HttpResponse.json({
      shortCode: params.code,
      clickCount: 10,
      lastClickAt: '2023-01-02T00:00:00Z',
      lastReferrer: 'https://google.com',
    });
  }),
];
