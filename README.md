# VerityScribe

Frontend foundation for an AI-powered clinical documentation companion. The app currently uses simulated repositories and realistic local domain data; no backend or AI services are called.

## Architecture

- `core/`: routing, constants, future API and storage abstractions.
- `models/`: serializable entities aligned to future backend resources.
- `repositories/`: interfaces plus mock implementations. Swap mocks for FastAPI-backed repositories without changing presentation code.
- `providers/`: Riverpod state and asynchronous repository access.
- `features/`: independently extensible product areas, including the staged authentication journey.
- `theme/` and `widgets/`: central design tokens and reusable UI primitives.

## Next backend step

Implement a `FastApiClient`, add repository implementations that call the endpoints in `core/constants/endpoints.dart`, then override the repository providers at app composition time.
