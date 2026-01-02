# Replay pagination

Use cursor paging for large sessions.

Endpoint:
- /v1/conduit/sessions/{sessionId}/replay/page?cursor=&limit=

Response:
- frames: array
- nextCursor: string or null
- hasMore: boolean

Client helper:
- ReplayPager
