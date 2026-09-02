# Dev orchestration only — the real code lives in backend/ and frontend/.
# `npm run dev` at the repo root does the same thing and works on Windows too;
# this is here for anyone who reaches for `make` out of habit.

.PHONY: dev down

dev:
	docker compose up -d
	@trap 'kill 0' EXIT; \
	(cd backend && mvn spring-boot:run) & \
	(cd frontend && npm install && npm run dev) & \
	wait

down:
	docker compose down
