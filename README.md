# ♟️ MateForge - Advanced Chess Endgame Training Platform

MateForge is a production-grade full-stack chess training platform designed to help players master technical checkmates against perfect defense. Unlike traditional chess applications, MateForge focuses exclusively on endgame conversion training, allowing players to practice mating patterns against engine-optimized resistance while receiving real-time feedback, analysis, and performance insights.

The platform combines modern web technologies, real-time communication, intelligent caching, and Stockfish-powered analysis to deliver a fast and immersive training experience.

---

## 🚀 Core Features

### 🎯 Endgame Training Modes
- King + Rook vs King
- Two Rooks vs King
- Queen vs King
- Two Bishops vs King
- Bishop + Knight vs King
- Two Pawns vs King
- Custom FEN Position Trainer
- Randomized Training Positions
- Daily Checkmate Challenges

### ⚡ Intelligent Engine Defense
- Stockfish-powered best legal defense
- Optimized engine communication
- Cached engine responses for repeated positions
- Fast move generation with asynchronous processing
- User timer pauses during engine calculations
- Optimal mating line generation and replay

### 📈 Learning & Analysis System
- Accuracy tracking
- Mistake and blunder detection
- Completion statistics
- Progress analytics dashboard
- Session history
- Favorite positions
- Daily challenge tracking
- Performance trends and improvement metrics

### 🎮 Interactive Training Experience
- Drag-and-drop chessboard
- Premoves support
- Takeback mode
- Hint system
- Move highlighting
- Legal move indicators
- Check, checkmate, stalemate and draw detection
- Animated move replay with arrows and board annotations

---

## 🏗️ System Architecture

MateForge follows a scalable enterprise architecture designed for performance and maintainability.

### Backend
- Java 21
- Spring Boot
- Spring Security
- JWT Authentication
- OAuth2 Google Login
- Spring Data JPA
- PostgreSQL
- Redis
- WebSocket / STOMP
- Stockfish UCI Integration

### Frontend
- React
- TypeScript
- Tailwind CSS
- React Chessboard
- Chess.js
- React Query
- Zustand State Management

---

## 🔥 Performance Optimizations

MateForge is heavily optimized to provide near-instant gameplay.

### Redis Caching
Redis is used extensively throughout the platform:

- FEN position caching
- Engine best-move caching
- Solution line caching
- Training session caching
- Leaderboard caching
- Analytics caching
- Frequently accessed position storage

### Asynchronous Processing
- Non-blocking Stockfish communication
- Background analysis tasks
- Cached replay generation
- Async session processing

### Frontend Optimizations
- Reduced React re-renders
- Lazy loaded modules
- Optimized state updates
- Fast board rendering
- Efficient websocket updates

---

## 🔐 Security Features

- JWT Authentication
- Refresh Tokens
- Google OAuth Login
- Password Encryption
- Role-Based Authorization
- CORS Protection
- Request Validation
- API Rate Limiting

---

## 📡 Real-Time Features

Using WebSockets and STOMP:

- Live training session updates
- Real-time timer synchronization
- Instant move broadcasting
- Session state synchronization

---

## 🗄️ Database & Storage

### PostgreSQL
Stores:

- Users
- Training Sessions
- Puzzle Attempts
- Move History
- Favorites
- Analytics
- Leaderboards
- Achievements

### Redis
Provides high-speed caching for:

- Engine responses
- Position evaluations
- Session state
- Frequently accessed analytics

---

## ☁️ Deployment Architecture

### Frontend
- Vercel

### Backend
- Render / AWS Ready

### Database
- PostgreSQL

### Cache Layer
- Redis

### Chess Engine
- Stockfish

---

## 🧠 Developer Highlights

This project demonstrates:

- Advanced Java Spring Boot development
- Secure authentication and authorization
- OAuth2 integration
- Real-time communication using WebSockets
- Redis-based performance optimization
- PostgreSQL database design
- Chess engine integration using UCI protocol
- Scalable REST API architecture
- Modern React + TypeScript frontend development
- State management and performance optimization
- Cloud deployment and production-ready architecture

---

## 🎯 Project Goal

MateForge aims to provide a focused training environment where players can repeatedly practice technical checkmates against perfect resistance, analyze mistakes, and improve endgame conversion skills through structured learning and engine-backed feedback.
