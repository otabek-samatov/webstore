# Webstore

Date Created  : 11/04/2025.

I am going to create  e-commerce application which sells goods. For now, it sells books.

I named it "Webstore".
---------------------------------------------------

It has following the services (each service is independent micsroservice):

Product Service – Handles product catalog (CRUD operations, categories, etc.)

Inventory Service – Manages stock levels.

Cart Service – Each user’s shopping cart logic.

Order Service – Order placement and tracking.

User Service – Registration, login, profile.

Gateway Service – API gateway using Spring Cloud Gateway.

Discovery Server – Service registry using Netflix Eureka or Consul.

Config Server – Centralized configuration using Spring Cloud Config.

Auth Service – With Spring Security and JWT/OAuth2.

------------------------------------------

The tech stack I am using:

Spring Boot: For rapid microservice development.

Spring Data JPA: For data persistence (Hibernate + JPA).

Spring Cloud: For distributed system essentials (Config Server, Eureka, Gateway).

Spring Security: For authentication and authorization (with JWT).

Apache Kafka: For event-driven communication between services.

------------------------------------------------------------------------------

Webstore is e-store project for selling books. It uses following technologies:

Java 21
Spring Boot 3.5
JPA (Hibernate implementation)
Spring Security
Spring Data
PostresDB 17.
Apache Kafka 4

I am developing only backend part. So, this project does not have UI.