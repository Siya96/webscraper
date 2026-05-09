.PHONY: up down logs build clean infra topics

up:
	docker-compose up -d

down:
	docker-compose down

logs:
	docker-compose logs -f

build:
	docker-compose build --parallel

infra:
	docker-compose up -d zookeeper kafka postgres redis elasticsearch

topics:
	docker exec stockpulse-kafka-1 bash /topics.sh

clean:
	docker-compose down -v --remove-orphans
