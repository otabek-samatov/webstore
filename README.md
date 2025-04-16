# Webstore

I am going to create  e-commerce application which sells goods. For now, it sells books.

I named it as "Webstore".
---------------------------------------------------

It has following services (each service is independent micsroservice):

Product Service – Handles product catalog (CRUD operations, categories, etc.)

Inventory Service – Manages stock levels.

Cart Service – Each user’s shopping cart logic.

Order Service – Order placement and tracking.

Payment Service – Handles payment processing.

User Service – Registration, login, profile.

Notification Service – For email/SMS/Telegram alerts.

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

Kafka events:

Event	          Producer	          Consumer
-----------------------------------------------------------------------------
product-created	  Product Service	  Search/Recommendation Services
order-placed	  Order Service	      Inventory Service, Notification Service
stock-depleted	  Inventory Service	  Notification Service
user-registered	  Auth Service	      Notification Service
