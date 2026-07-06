# Mateforge

Full-stack checkmate training platform for practicing technical mates against best legal defense.

## Stack

- Backend: Java 21, Spring Boot, Spring Security JWT, Spring Data JPA, PostgreSQL
- Realtime: Spring WebSocket/STOMP topic updates at `/topic/sessions/{sessionId}`
- Chess: `chesslib` for legal move/state validation, Stockfish UCI for best defense and solution lines
- Frontend: React, TypeScript, Tailwind CSS, `react-chessboard`, `chess.js`

## Features Included

- Training modes: K+R, two rooks, queen, two bishops, bishop+knight, two pawns, custom FEN, random
- Difficulty and timer modes
- Legal move blocking
- Engine-backed defender response
- Hints with usage tracking
- Takeback support when enabled
- Check/checkmate/stalemate/draw state messaging
- Move-by-move optimal solution endpoint and visual replay
- Optional Gemini-generated session and rolling player profile reports
- Accuracy, mistakes, completion history, favorites, daily puzzles, leaderboard-ready records
- Authenticated REST API with rate limiting and CORS
- Light/dark responsive UI

## API Surface

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/puzzles`
- `GET /api/puzzles/daily`
- `POST /api/sessions`
- `GET /api/sessions`
- `GET /api/sessions/{id}`
- `POST /api/sessions/{id}/moves`
- `POST /api/sessions/{id}/hint`
- `POST /api/sessions/{id}/undo`
- `GET /api/sessions/{id}/solution`
- `GET /api/sessions/{id}/report`
- `GET /api/analytics/progress`
- `GET /api/analytics/profile`
- `GET /api/analytics/profile-report`
- `GET /api/favorites`
- `POST /api/favorites`
- `GET /api/leaderboard`

## Run Locally

1. Start PostgreSQL:

```bash
docker compose up -d
```

2. Install Stockfish and make sure `stockfish` is on your PATH, or set:

```bash
set STOCKFISH_PATH=C:\path\to\stockfish.exe
```

3. Run the backend from `backend/`:

```bash
mvn spring-boot:run
```

If Maven is not installed, install Maven or add a Maven Wrapper with `mvn -N wrapper:wrapper`.

4. Run the frontend from `frontend/`:

```bash
npm install
npm run dev
```

Open `http://localhost:5173`.

## Environment

Backend defaults are in `backend/src/main/resources/application.yml`.

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `STOCKFISH_PATH`
- `STOCKFISH_MOVE_TIME_MS`
- `STOCKFISH_LINE_DEPTH`
- `GEMINI_API_KEY`
- `GEMINI_MODEL`
- `GOOGLE_CLIENT_ID`

Frontend:

- `VITE_API_BASE`
- `VITE_GOOGLE_CLIENT_ID`

## Deploy

### Vercel Frontend

Use the `frontend` folder as the app root.

```txt
Framework preset: Node
Root directory: frontend
Build command: npm run build
Output directory: dist
Start command: leave blank
```

Environment variable:

```txt
VITE_API_BASE=https://your-render-backend.onrender.com
```

### Render Backend

Create a Docker web service from this repository.

```txt
Environment: Docker
Root directory: backend
Dockerfile path: Dockerfile
```

Environment variables:

```txt
DATABASE_URL=jdbc:postgresql://HOST:PORT/DATABASE
DATABASE_USERNAME=DATABASE_USER
DATABASE_PASSWORD=DATABASE_PASSWORD
JWT_SECRET=long-random-secret-at-least-64-characters
CORS_ALLOWED_ORIGINS=https://your-vercel-app.vercel.app
STOCKFISH_PATH=/usr/games/stockfish
STOCKFISH_MOVE_TIME_MS=250
STOCKFISH_LINE_DEPTH=14
GEMINI_API_KEY=your-google-ai-studio-key
GEMINI_MODEL=gemini-2.5-flash
GOOGLE_CLIENT_ID=your-google-oauth-client-id
RATE_LIMIT_RPM=120
PORT=8080
```

When using Render PostgreSQL, convert the database connection to JDBC format:

```txt
postgresql://user:password@host:5432/dbname
```

becomes:

```txt
jdbc:postgresql://host:5432/dbname
```

Then put `user` and `password` into `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.

## Notes

The frontend has local fallback positions so the board can be explored before the backend is running. Engine defense, persisted sessions, hints, accuracy, and optimal solution replay require the Java API and Stockfish.
