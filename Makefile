.PHONY: build init init-db

init: init-db build

init-db:
	docker compose up -d

build:
	docker build -t read-story:latest .

run:
	docker compose -p story up -d