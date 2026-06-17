# HSclubs — Deployment Guide

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────┐
│   Browser    │────▶│   Nginx      │────▶│  Vue SPA │
│              │     │   :80/:443   │     │  :4173   │
└──────────────┘     └──────┬───────┘     └──────────┘
                            │
                            ▼
                     ┌──────────────┐     ┌──────────┐
                     │ Spring Boot  │────▶│  MySQL   │
                     │   :8080      │     │  :3306   │
                     └──────────────┘     └──────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │ Google OAuth │
                     └──────────────┘
```

## Prerequisites

| Component | Minimum Version | Purpose |
|-----------|----------------|---------|
| Java | 17+ | Backend runtime |
| MySQL | 8.0+ | Database |
| Node.js | 20+ | Frontend build |
| npm | 10+ | Frontend dependencies |
| Nginx | 1.24+ | Reverse proxy (production) |
| Google Cloud Project | — | OAuth2 credentials |
| Domain | — | HTTPS + OAuth redirect |
