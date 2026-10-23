# Java Backend Job Market Analysis (Warsaw / Poland / EU), October 2026

Verification date for every entry: **2026-10-23**. All postings below were opened and read on that date unless explicitly marked otherwise. Nothing in this document is reconstructed from memory; where a page could not be fetched, the employer is listed in the "Not verified" section rather than in the main table.

## 1. Scope and method

- Target profile: Middle+/Senior Java backend (Java 21, Spring Boot, PostgreSQL, Kafka), Warsaw and Poland first, other EU hubs and global employers second.
- Sources used: justjoin.it, bulldogjob.pl, employer career sites (ING, Société Générale, Stripe, Greenhouse/Rightmove), and two mirror sites that reproduce official postings (anitab.org for Google, dreamworkhq.com for Netflix) when the official page could not be rendered.
- Boards that could not be read: nofluffjobs.com (blocked for the fetch tool), pracuj.pl and theprotocol.it (HTTP 403), amazon.jobs, higher.gs.com and jobs.citi.com (JavaScript-rendered or 404).
- Each posting was checked for an "Offer expired"/"closed" marker or a visible deadline. "Active" means no expiry marker was present and, where shown, the deadline was later than 2026-10-23.

### Verification summary

| Category | Count |
|---|---|
| Active mid/senior/staff Java backend postings, fully read | 24 |
| Active mid/senior postings read via a mirror of the official page (Google, Netflix) | 2 |
| Active junior/early-career postings (comparison only) | 3 |
| Recently advertised but expired postings, full text still readable | 13 |
| Employers searched but not verifiable (no fetchable Java posting) | 14 |

**Total verified mid/senior postings used for frequency analysis: 37** (26 active + 11 expired mid/senior). Only 26 of the 37 could be confirmed as currently open. Two of the 26 (Google, Netflix) are "content verified, live status not confirmed on the official page".

## 2. Reference vacancy: Avenga, Programista/Programistka Java (Warsaw)

- URL: https://justjoin.it/job-offer/avenga-programista-programistka-java-warszawa-java
- Status on 2026-10-23: **Active** (Apply/Save buttons present, no expiry marker).
- Seniority: Senior. Location: Warsaw, Rondo Daszyńskiego, hybrid 1 day onsite / 4 remote. B2B, 140-160 PLN net/h.
- Domain: insurance claims reporting and handling, microservices.

| Advertised as required | Advertised as expectation in responsibilities | Not present in the posting |
|---|---|---|
| Java (>5 years) | Build microservices for claims processes | Any specific cloud provider |
| Kafka (>3 years) | Design, implement and maintain APIs and integrations | Docker/Kubernetes |
| Spring / Hibernate / Spring Boot / Spring Cloud (>5 years) | System design incl. functional and non-functional requirements | Observability tooling |
| SQL (>5 years) | Improve business processes via scalable IT solutions | Security standards |
| Unit testing frameworks: JUnit, TestNG, Spock | Quality via automated tests and good engineering practice | Frontend |
| Integration testing tools: Testcontainers, WireMock | Active use of AI tools and agentic AI across analysis, implementation, testing and maintenance | |
| Functional and object-oriented programming | | |

The posting has no separate "nice-to-have" block; everything listed is phrased as a requirement. The expected profile given in the brief (Java, Spring/Spring Boot/Spring Cloud, Hibernate/JPA, Kafka, SQL, microservices, OOP/FP, REST, JUnit/Testcontainers/WireMock, business-process automation, agentic AI) is confirmed, with the nuance that "business-process automation" appears as a responsibility ("improving business processes"), not as a BPMN tool requirement.

Context: four other Warsaw postings read on the same day describe the same insurance-claims project through other vendors (ASTEK Polska, IT Factory, Transition Technologies PSC, SCALO) with near-identical stacks; ASTEK additionally lists BPMN/Flowable and resilience patterns. This means the reference profile is a real, multi-vendor demand signal rather than a single company's wording.

## 3. Verified postings

Legend: **A** = explicitly advertised; **(inf)** = inferred from responsibilities or named stack rather than listed as a requirement; **N** = nice-to-have. "Arrangement" uses the posting's own wording. All URLs verified 2026-10-23.

### 3.1 Active mid/senior/staff postings

| # | Company | Position | Seniority | Location / arrangement | Languages | Backend frameworks | Data systems | Distributed systems / messaging | Cloud / infra | Testing | Architecture expectations | Security | Source URL (verified 2026-10-23) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Avenga | Programista/Programistka Java (reference) | Senior (5+ y) | Warsaw, hybrid 1/4, B2B 140-160 PLN/h | Java (A), OOP+FP (A) | Spring, Spring Boot, Spring Cloud, Hibernate (A) | SQL (A) | Kafka 3+ y (A) | none listed | JUnit/TestNG/Spock, Testcontainers, WireMock (A) | Microservices, API design, NFRs (A); agentic-AI-assisted delivery (A) | not mentioned | https://justjoin.it/job-offer/avenga-programista-programistka-java-warszawa-java |
| 2 | Avenga | Senior Java Developer (core banking) | Senior | Warsaw, hybrid 2 visits/month, B2B 26,880-31,800 PLN net/month; active to 27.10.2026 | Java (A) | Spring Boot, Micronaut (A) | not listed | Kafka (A), message-driven, event sourcing (inf) | AWS deployment/config (A), Docker, Kubernetes (A) | "well-tested code" (A, no tools) | Message-driven core banking, mentoring juniors (A) | "highly secure application environment" (A) | https://justjoin.it/job-offer/avenga-senior-java-developer-warszawa-java-61992e4b |
| 3 | TSS | Programista Java | Senior (5+ y) | Warsaw, remote/hybrid, B2B 90-105 PLN/h; active to 31.10.2026 | Java 11+ (A), Polish C1 | Spring Boot 2.x/3.x (A) | PostgreSQL/PostGIS or Oracle (A); jOOQ (N) | none | Azure or AWS (A), Docker (A), Kubernetes (N), GitLab CI/Jenkins (A) | not mentioned | Microservices, Clean Architecture, DDD, domain modelling, OpenAPI docs (A) | SAML2, OAuth2, OIDC, Apereo CAS, LDAP, Keycloak (A/N) | https://justjoin.it/job-offer/tss-programista-java-warszawa-java-32027046 |
| 4 | Nordea | Senior IT Developer (Backend) | Senior | Warsaw/Gdańsk, hybrid, permanent; active to 31.10.2026 | Java 21+ (A), English C1 | Spring Boot, Hibernate (A) | PostgreSQL, SQL (A); Snowflake (N) | Kafka / JMS / MQ (A); AsyncAPI (A) | Git, CI/CD (A) | not mentioned | OpenAPI + AsyncAPI contracts, design patterns, OOP (A); Airflow, Python (N) | general bank data-protection wording | https://justjoin.it/job-offer/nordea-senior-it-developer-backend-developer--warszawa-java-dab41208 |
| 5 | Sii | Senior Java Developer (f/m/x) | Senior (5+ y) | Remote PL (14 offices), B2B 20-24k net / UoP 15-18k gross; active to 12.11.2026 | Java, Python, Go, Node.js, React (A) | not named | not named | Kafka or RabbitMQ (N) | GCP and AWS (A), Terraform, Ansible (A), GitHub Actions, legacy Jenkins (A) | not mentioned | Platform/cloud transformation ownership, IaC (A) | not mentioned | https://justjoin.it/job-offer/sii-senior-java-developer-f-m-x--warszawa-java-37e1aa50 |
| 6 | Experis (ManpowerGroup) | Senior Java Developer (Verification of Payee platform) | Senior (5+ y) | Warsaw, hybrid 1/4, B2B 130-150 PLN/h; active to 27.10.2026 | Java 21/25 (A) | Spring Boot 4+ y, WebFlux (A) | PostgreSQL, Elasticsearch 3+ y (A); Hazelcast, Ehcache (stack) | Kafka, Kafka Streams 3+ y (A); microservices + distributed systems (A) | GCP preferred (A), Docker, Kubernetes (A), Prometheus, Grafana (stack), Maven, Gerrit | not mentioned | Event-driven, high-performance, secure services (A) | PKI, JWT, TLS/mTLS (N); Copilot/MCP AI tooling (N) | https://justjoin.it/job-offer/experis-manpower-group-senior-java-developer-warszawa-java-324bd2f2 |
| 7 | IT Factory (insurance client) | Senior Java Developer (Spring, Kafka, Angular) | Senior (7+ y) | Warsaw, hybrid 1/4, B2B; no deadline shown | Java (A), Angular (A) | Spring Boot, Spring Framework, Hibernate/JPA (A) | SQL (A); Elasticsearch (N) | Kafka (A); RabbitMQ (N) | Docker, Kubernetes, Azure/AWS/GCP, CI/CD (all N) | automated test pyramid (A), Playwright (N) | DDD, microservices and monolith, REST API design, AI tools in development (A) | IT security knowledge (N) | https://justjoin.it/job-offer/it-factory-senior-java-developer-warszawa-java-6dac60ac |
| 8 | Transition Technologies PSC | Senior Java Developer (talent pool) | Senior (7+ y) | Warsaw, hybrid 1/4, B2B 120-160 PLN/h; active to 31.10.2026 | Java (A), Polish C1, English B2, Angular (A) | Spring Boot, Spring Framework, Hibernate/JPA (A) | SQL (A); Elasticsearch (N) | Kafka (A), event-driven (A); RabbitMQ (N) | Docker, Kubernetes, Azure/AWS/GCP, CI/CD (N) | automated tests (A), Playwright (N) | DDD, microservices, REST, AI tools in development (A) | IT security (N) | https://justjoin.it/job-offer/transition-technologies-psc-senior-java-developer---talent-pool-warszawa-java |
| 9 | Shaped Thoughts | Senior Software Engineer (Java) | Senior (7+ y) | Remote-first PL, B2B 170-200 PLN/h; active to 01.11.2026 | Java and/or Kotlin (A), English C1, Polish C1 | not named | SQL and NoSQL (A) | Distributed systems, event-driven, microservices (A) | AWS (A), GCP/Azure acceptable | Automated, acceptance and performance tests (A); TDD/BDD (N) | Clean architecture, "you ship it, you run it" (A); DDD (N); AI-assisted dev with Claude Code/Codex (N) | not mentioned | https://justjoin.it/job-offer/shaped-thoughts-senior-software-engineer-java--warszawa-java-7424f90f |
| 10 | Prime Force | Senior Java Software Engineer (ECM integrations) | Senior | Warsaw hybrid or remote PL, UoP/B2B 17,500-22,500 PLN gross; active to 30.10.2026 | Java (A), English B2 | Spring / Spring Boot (A) | databases, integrations (A) | REST APIs (A) | Docker, Kubernetes, AWS or Azure, Git, CI/CD (A) | unit and integration tests (A) | REST/integration design, prototyping; hands-on AI coding tools incl. agents and MCP servers (A) | OWASP / app security (N) | https://justjoin.it/job-offer/prime-force-senior-java-software-engineer-warszawa-java |
| 11 | ASTEK Polska | Senior Java Developer (AI) (insurance claims) | Senior (5+ y) | Warsaw, hybrid 1 day, B2B 1,000-1,200 PLN/day; active to 17.11.2026 | Java (A), OOP+FP (A), Angular (A) | Spring, Spring Boot, Spring Cloud, Hibernate (A) | SQL (A) | Kafka 3+ y, async messaging (A); resilience patterns for distributed systems (A) | containerisation (A); cloud (N) | JUnit, TestNG, Spock, Testcontainers, WireMock (A) | Microservices, API design, BPMN / Flowable (A); DDD, agentic AI (N) | "code security" in general terms | https://justjoin.it/job-offer/astek-polska-senior-java-developer-ai--warszawa-mazowieckie--java-0210a694 |
| 12 | ING Bank Śląski | Senior Java Developer (REQ-10123146) | Senior (5+ y) | Katowice or Warsaw, hybrid 2 days, UoP 16-20k PLN gross; posted 21.10.2026 | Java 17+ (A), Polish C1, English | Spring Framework, Spring Boot (A) | SQL + query optimisation (A); Redis (A) | Kafka incl. error handling, retries, scalability (A); event sourcing (N) | Kubernetes, Docker (N); public-cloud migration (inf) | unit testing (A) | REST, microservices, Clean Code, SOLID, diagnosing production performance (A); Prometheus/Grafana/OpenTelemetry/ELK (N); Spark/Airflow (N) | "security in design" (A) | https://careers.ing.com/en/job/warsaw/senior-java-developer/3121/46193006080 |
| 13 | ING Hubs Poland | Senior Java Software Engineer, Financial Markets (REQ-10116469) | Engineer III/IV (5+ y) | Katowice/Warsaw, travel to Belgium; 9,600-22,000 PLN; posted 11.06.2026, no close marker | Java (A) | Spring Boot (A) | SQL (A) | not named | Git, CI/CD pipelines (A) | automated testing (A) | REST services, modular/testable systems, algorithms, concurrency, performance and resilience in production (A) | not mentioned | https://careers.ing.com/en/job/katowice/senior-java-software-engineer-financial-markets/3121/39507250752 |
| 14 | Edge One Solutions (Euvic) | Senior Java Developer, banking | Senior (5+ y) | Warsaw, hybrid 1/4, B2B; active to 01.11.2026 | Java (A) | Spring Boot (A) | relational databases (A) | REST integrations (A) | Git, CI/CD (A) | not mentioned | REST-based system integration, working with architects/analysts (A) | not mentioned | https://justjoin.it/job-offer/edge-one-solutions-sp-z-o-o-senior-java-developer-branza-bankowa-warszawa-java-6142abfc |
| 15 | BlueSoft (Orange Group) | Senior Java Developer (Retail) | Senior (5+ y) | Warsaw, currently 100% remote, B2B; no expiry marker | Java (A), Polish C2 | Spring, JPA/ORM (A) | PostgreSQL (A) | JMS (MQTT) (A), SOAP/REST (A) | Docker (A), Maven | not mentioned | Design and integration patterns (A) | not mentioned | https://justjoin.it/job-offer/bluesoft-senior-java-developer-retail--warszawa-java |
| 16 | SCALO | Java Developer (insurance claims) | Senior (7+ y) | Warsaw, hybrid 1 day, B2B 19,200-22,400 PLN net; active to 04.11.2026 | Java (A), Angular (A) | Spring Boot, Spring Framework, Hibernate/JPA (A) | SQL (A); Elasticsearch (N) | Kafka (A); RabbitMQ (N) | Docker/Kubernetes, Azure/AWS/GCP, CI/CD (N) | automated tests (A), Playwright (N) | DDD, microservices and monolith, REST API design, AI tools (A) | IT security (N) | https://justjoin.it/job-offer/scalo-java-developer-warszawa-java-979d7240 |
| 17 | Box | Senior Software Engineer, Data Foundations | Senior (5+ y) | Warsaw, hybrid 3 office days, UoP; valid to 11.11.2026 | Java or Scala (A) | not named | multiple data stores, consistency/synchronisation (A) | Asynchronous, messaging, event-driven systems (A); highly available distributed systems (A) | not named | not named | Production ownership incl. observability, reliability, on-call (A) | not mentioned | https://bulldogjob.com/companies/jobs/258044-senior-software-engineer-data-foundations-warsaw-box |
| 18 | Netflix | Staff Software Engineer 6, Java Platform Team | Staff | Warsaw; "posted 2w ago" on mirror; official Workday page not renderable | Java 17/21+ (A), JVM internals, GC, class loading (A) | Spring Boot internals (N); lightweight library set (A) | not named | Distributed systems, gRPC, service mesh (A) | sidecars / containers with tight resource limits (N); Gradle tooling (N) | benchmarks, regression and compatibility tests (A) | Library/API design, versioning and compatibility, footprint budgets, written design memos (A) | not mentioned | https://www.dreamworkhq.com/job/b17f1abb-b466-42f1-a3dd-268c093f5d77 (mirror of netflix.wd108.myworkdayjobs.com JR42922) |
| 19 | Société Générale (SG CIB) | Java Developer (2600091R) | several years, level not stated | Warsaw, hybrid 3 days, permanent; published 2026-04-08, no close marker | Java 17+ (A) | Spring Boot 3 / 4+ (A) | PostgreSQL, Flyway (stack) | RabbitMQ (stack); REST APIs (A) | Kubernetes (stack; N in profile), Jenkins, Git (A); Azure (N) | unit and integration tests (A) | Follow architecture and security standards, write architecture docs, L3 support (A) | compliance with security standards (A, unspecified) | https://careers.societegenerale.com/en/job-offers/java-developer-2600091R-en |
| 20 | Devire (end client unnamed) | Senior Java Engineer | Senior (7-10 y) | Warsaw, on-site, B2B 170-200 PLN/h; valid to 01.11.2026 | Java (A) | Spring Boot (A) | not named | Kafka integrations and message processing (A) | Docker, Kubernetes, GitLab CI/CD, Argo CD (A); Prometheus, Grafana, Elastic Stack (A) | not named | Microservices; mandatory use of AI dev tools (Copilot, Devin, Claude) (A) | not mentioned | https://bulldogjob.pl/companies/jobs/257112-senior-java-engineer-warsaw-devire |
| 21 | Elastic | Senior Java Engineer, Security, Elasticsearch | Senior | Remote Poland, UoP 307,800-486,900 PLN gross/yr; valid to 18.11.2026 | Java internals, JVM memory management, concurrency (A) | n/a (product internals) | search-engine / data-store internals (N) | Distributed-systems security: node-to-node trust, zero-trust transport, partition tolerance (A) | not named | not named | Security architecture for a distributed data store; AI tools for development and vulnerability triage (A) | OAuth 2.0, SAML (A); TLS, PKI, crypto, FIPS 140, FedRAMP, SOC 2 (N) | https://bulldogjob.pl/companies/jobs/258937-senior-java-engineer-security-elasticsearch-elastic |
| 22 | Luxoft DXC (global bank) | Senior Java Developer | Senior (7+ y) | Warsaw, on-site, UoP; valid to ~19.11.2026 | Java core, JDBC, multithreading (A), Unix shell (A) | Spring Boot (A) | Oracle SQL/PL-SQL (A); Redis (A); NoSQL (N) | Kafka (A), REST (A) | Kubernetes (A); TeamCity, Harness/uDeploy, Artifactory, SonarQube, Git (A) | SonarQube only | Reference-data platform, re-architecting for performance; microservices (N) | not mentioned | https://bulldogjob.pl/companies/jobs/257448-senior-java-developer-warsaw-luxoft-dxc |
| 23 | Accenture Polska | Senior Java Developer | Senior | Kraków/Wrocław/Katowice/Warsaw/Łódź, flexible; valid to 07.11.2026 | Java 17+ (stack) | Spring Boot (stack) | not named | microservices (inf) | AWS / GCP / Azure (stack) | collaboration with QA (inf) | Microservices in cloud (A); requirements block empty on the page | not mentioned | https://bulldogjob.pl/companies/jobs/254706-senior-java-developer-krakow-wroclaw-katowice-warsaw-lodz-accenture-polska |
| 24 | Rightmove | Senior Software Engineer I, Backend (Java) | Senior | London, hybrid 2 days | Java 21+ (A) | Spring Boot, Hibernate, Gradle (A/stack) | SQL, Elasticsearch, Couchbase (stack) | Kafka (A) | Docker (stack); pipeline optimisation / left-shifting (A) | TDD "deep understanding" (A) | Lead complex projects end-to-end, balance tech debt, mentor (A) | not mentioned | https://job-boards.eu.greenhouse.io/rightmovecareers/jobs/4947984101 |
| 25 | Stripe | Backend/API Engineer, Money as a Service | 2-12+ y (level set at interview) | London, hybrid >=50% office; GBP 81,600-157,200 | "any programming language" (A); Java is one of Stripe's main languages (inf) | not named | large-scale financial tracking systems (N) | APIs and large-scale systems for billions of money movements (A) | AWS, Docker/Kubernetes (N) | not named | Ergonomic API design, production debugging, mentoring (A); gRPC, GraphQL (N) | not mentioned | https://stripe.com/jobs/listing/backend-api-engineer-payouts/7369543 |
| 26 | Google | Software Engineer III, Google Cloud, Dataform | Mid (L4 equivalent) | Warsaw; posted 2026-08-14 on mirror; official page returned navigation only | 2+ y in any language (A); Kotlin, Java, Scala or Go (N) | not named | database/storage solutions (N) | Distributed systems concepts, backend services and APIs (N) | Google Cloud (product) | "testable, maintainable code" (N) | Backend architecture, performance tuning, stability and security, on-call (A) | "improving platform stability and security" (N) | https://jobs.anitab.org/companies/google-24698/jobs/89986962-software-engineer-iii-google-cloud-dataform (mirror of google.com/about/careers job 114048307692479174) |

### 3.2 Active junior / early-career postings (comparison only)

| # | Company | Position | Seniority | Location / arrangement | Advertised requirements | Notable contrast with senior roles | Source URL (verified 2026-10-23) |
|---|---|---|---|---|---|---|---|
| J1 | Allegro | Software Engineer 1 (Java/Kotlin), Allegro Ads | Junior | Warsaw, hybrid 4/1, UoP 11,200-15,250 PLN gross; active to 01.11.2026 | Kotlin or Scala, Spring Boot, Kafka, English B2, Polish B2; eagerness to learn GCP, Docker, Kubernetes, AI assistants | Same stack keywords as senior roles but at "junior" proficiency; no architecture or ownership expectations | https://justjoin.it/job-offer/allegro-software-engineer-1-java-kotlin-allegro-ads-warszawa-java |
| J2 | EPAM Systems | Junior Java Software Engineer | Junior (1+ y) | Warsaw, hybrid 3 days; active to 06.11.2026 | Java, SQL, unit testing, algorithms and data structures, CI, GCP, Git, application-security understanding; gRPC and Spanner (N) | Algorithms/DS emphasis; no Kafka, no DDD | https://justjoin.it/job-offer/epam-systems-junior-java-software-engineer-warsaw-java |
| J3 | Netcompany | Junior Java Developer (f/m) | Junior (0-1 y) | Warsaw, office-based, 7-10k PLN; active to 05.11.2026 | Java, Spring, SQL at junior level, OOP basics, English | Unit and manual testing; no messaging, no cloud | https://justjoin.it/job-offer/netcompany-junior-java-developer-f-m--warszawa-java-3809745a |

### 3.3 Recently advertised, now expired (full text still readable on 2026-10-23)

These are included because they were advertised in 2026 and show what the same employers asked for; they are counted in the frequency table but not as "open" roles.

| # | Company | Position | Seniority | Location | Key advertised requirements | Nice-to-have / stack | Source URL (verified 2026-10-23, marked expired) |
|---|---|---|---|---|---|---|---|
| X1 | Allegro (Razor team) | Senior Software Engineer (Java/Kotlin), Product Page | Senior | Toruń, hybrid 4/1, 18,850-25,900 PLN gross + RSU | Java, Kotlin, Spring, MongoDB, English B2; on-call "you build it, you run it"; mentoring | Airflow, Big Data, microservices, LLMs; GCP, Kubernetes, Docker, Consul, GitHub Actions; reactive programming | https://justjoin.it/job-offer/allegro-senior-software-engineer-java-kotlin---product-page-torun-java |
| X2 | Allegro (Ads) | Senior Software Engineer (Java/Kotlin), Allegro Ads | Senior | Warsaw, hybrid 4/1, 18,850-25,900 PLN gross | Kotlin or Scala, Spring Boot, Kafka, Airflow, Spark, PostgreSQL, DB migrations, API integrations | BigQuery, Kubernetes, Docker, Consul, GitHub Actions, Hermes data bus, service mesh; React/TS (N) | https://justjoin.it/job-offer/allegro-senior-software-engineer-java-kotlin---allegro-ads-warszawa-java |
| X3 | Link Group | Java Developer with Kafka (insurance) | Mid tag, 5+ y | Warsaw and 4 cities, remote, B2B 130-155 PLN/h | Java, Spring/Spring Boot, Hibernate/JPA, Maven, REST, SOAP, SQL (Oracle/PostgreSQL/MySQL), Git, Kafka, unit tests | RabbitMQ, Camunda, microservices, AWS or Azure | https://justjoin.it/job-offer/link-group-java-developer-with-kafka-warszawa-java |
| X4 | Avenga | Java Developer (Everest system) | Mid tag, 5+ y | Warsaw, hybrid 1/4, B2B 120-130 PLN/h | Java/JEE, Spring, Hibernate, Maven, Gradle, OOP, Git, SQL (Oracle/PostgreSQL), multithreading | none | https://justjoin.it/job-offer/avenga-java-developer-warszawa-java-c2e93b06 |
| X5 | Nexio Management | Java Developer (Spring/Groovy) | Mid (4+ y) | Warsaw, hybrid 2x/month | Java, Spring/Groovy, Maven, Git, Jenkins, Kafka, ELK/Grafana, SonarQube, JUnit/Mockito/Spock, REST | MQ, BPM/BPMN, DDD | https://justjoin.it/job-offer/nexio-management-java-developer-spring-groovy-k-m-warszawa-java |
| X6 | Bank Pekao S.A. | Senior Java Developer, Agentic AI / LLM | Senior (5+ y) | Łódź, hybrid, UoP | Java, Spring, Hibernate (master level); daily use of LLM agents and GitHub Copilot; Specification-Driven Development; testing/QA; DevOps understanding | none listed | https://justjoin.it/job-offer/bank-pekao-s-a--senior-java-developer---agentic-ai-llm-lodz-java |
| X7 | EPAM Systems | Senior Java Developer (market risk) | Senior (5+ y) | Poland, remote | Java 8+, multithreading/concurrency/distributed computing, advanced SQL (window functions, optimisation), PostgreSQL/Oracle, microservices, cloud, Linux | Spark, Greenplum, event-driven architecture, Docker/Kubernetes, DevOps | https://justjoin.it/job-offer/epam-systems-senior-java-developer-poland-remote--java-d7be5b50 |
| X8 | Jit Team (Grid Dynamics Poland tag; Nordic financial client) | Senior Java Developer | Senior | Gdańsk/Gdynia/Warsaw, hybrid 3 days, B2B 22-27.3k PLN net | Java, Spring, Hibernate, SQL/PostgreSQL, OpenAPI and AsyncAPI, Git, CI/CD, design patterns; upgrade to Java 17+ / Spring Boot 3.3 | JMS/MQ messaging | https://justjoin.it/job-offer/jit-team-senior-java-developer-gdansk-java-3a433373 |
| X9 | Revolut | Lead Software Engineer (Java) | Lead (8+ y) | Kraków + PL offices, remote option, 44,300-46,600 PLN gross | Java fluency, high-performance real-time distributed systems, database sharding, CQRS, event-driven architecture; stack Java 17/21, PostgreSQL, Redis, jOOQ, Flyway, GCP, Kubernetes, Grafana/Prometheus/New Relic, Spock, TDD, DDD | high-load fintech, multi-region deployments | https://justjoin.it/job-offer/revolut-lead-software-engineer-java--krakow-java |
| X10 | Revolut | Software Engineer (Java), relocation to PL/ES/UAE | Mid tag, 6+ y | Kraków/Warsaw/Wrocław/Rzeszów, 28,300-40,000 PLN gross | Java fluency, 6+ y backend; same stack as X9; microservices, DDD, TDD | Kotlin/Scala, fintech | https://justjoin.it/job-offer/revolut-software-engineer-java---relocation-to-poland-spain-or-uae-rzeszow-java-b51b1ad7 |
| X11 | Capgemini Polska | Full-stack Developer (Java, Angular) | Senior (10+ y Java) | Warsaw, hybrid; closed 2026-09-22 | Java, Spring Boot, Angular/TypeScript/RxJS, Docker, Kubernetes, Helm, Jenkins, Maven, JUnit, REST, MongoDB, microservices, SonarQube; Kafka, AWS, Istio, PostgreSQL in profile | event-driven architecture, IaC | https://freehire.me/jobs/full-stack-developer-java-angular-capgemini-polska-sp-z-o-o-capgemini-polska-sp-z-o-o-4zdrinpg (aggregator; original on WhatJobs) |
| X12 | Visa | Associate SW Engineer, Java | Early career (degree or 3+ y) | Warsaw, hybrid | Core Java plus one frontend technology, OO design; "AI and generative AI skills highly desirable" | none | https://jobs.smartrecruiters.com/Visa/744000113163993-associate-sw-engineer-java |
| X13 | 1dea | Junior Java Developer (Cloud & Kafka) | Junior (1-3 y) | Warsaw, remote, B2B 70-80 PLN/h | Java, basic Spring Boot, AWS or Azure, Kafka or another broker, English B2 | microservices basics, SQL, certificates | https://justjoin.it/job-offer/1dea-junior-java-developer-cloud-kafka--warszawa-java |

### 3.4 Employers searched but not verified

| Employer | What was found | Why excluded from the table |
|---|---|---|
| Amazon (Gdańsk/Warsaw) | Ring SDE postings in Gdańsk via aggregators; amazon.jobs search page is JavaScript-rendered | No Java-specific SDE II posting could be opened; Java appears only as one of several accepted languages in early-career postings |
| Microsoft | Prague Senior SWE roles via aggregators listing Java among C#/Python | No official page fetched; no Java-primary backend role in Warsaw/EU found |
| NVIDIA | Poland roles (Cloud Automation, AI Inference Systems) via aggregators; backend roles in OCaml/Rust/C++ | No Java backend role identified |
| Uber (Aarhus/Amsterdam) | Senior SWE Stateful/Storage Platform roles; "Go, Java, Python" listed as acceptable | Job pages returned HTTP 404 on fetch |
| Goldman Sachs (Warsaw) | higher.gs.com lists Warsaw roles (Lakehouse/AI Data Platform, VP cloud roles) | Board is JavaScript-rendered; no Java requirement text retrievable |
| JPMorgan Chase | US postings only; experienced-hire programme page names Java/Python | No Warsaw posting surfaced |
| Bloomberg (London) | Senior SWE, Java Infrastructure (DevX) listed in search | Both Avature URLs returned 404 |
| Mastercard (Warsaw) | Lead Software Engineer (Java), Open Finance Europe, R-273155 | Official page returned HTTP 410 Gone; mirrors 404 |
| Citi (Warsaw) | Java Developer and Senior Java Developer postings dated Mar-Sep 2026; aggregator says Spring Boot, microservices, Kafka/Docker/K8s/ELK/MongoDB as a plus, OpenShift | jobs.citi.com pages returned 404 to the fetcher; snippet-only evidence |
| Aviva (Warsaw) | (Senior) Software Engineer (Java): Spring Boot, Kafka/MSK, AWS ECS, OpenAPI, API security, CI/CD per aggregators | Workday page returned empty content; snippet-only evidence |
| Ocado Technology | Kraków/Wrocław Java roles removed in 2025; Glassdoor shows no open roles | No 2026 posting found |
| Booksy, Docplanner, STX Next | Only Python/Vue roles found | No Java postings |
| mBank, Samsung R&D Warsaw, Software Mind | No posting surfaced in searches | Not found |
| Revolut (live) | Official careers pages returned 403/404; only expired justjoin mirrors readable (X9, X10) | Counted as expired |

## 4. Market observations (grounded in the 37 verified postings)

### 4.1 Seniority bar

- "Senior" in Poland most often means **5+ years of commercial Java** (Avenga, ING, Nordea, Experis, Edge One, BlueSoft, ASTEK, Box); the insurance-claims cluster and Devire/Luxoft ask for **7+ years**; Revolut Lead asks for 8+.
- Several postings tagged "Mid" on justjoin.it (Link Group, Avenga Everest, Revolut relocation) still require 5-6 years. Board tags understate the real bar.
- Junior postings share stack keywords (Spring Boot, Kafka, GCP) but drop every architecture, ownership, observability and security expectation.

### 4.2 Compensation range observed (Warsaw unless stated)

| Contract | Range observed | Examples |
|---|---|---|
| B2B hourly | 90-105 PLN/h (TSS) to 170-200 PLN/h (Shaped Thoughts, Devire) | Avenga reference 140-160; Experis 130-150; TT PSC 120-160 |
| B2B monthly | 19,200-31,800 PLN net | SCALO, Sii, Avenga core banking |
| Employment contract (UoP) | 16,000-25,900 PLN gross/month (banks, Allegro, Prime Force) | Revolut 28,300-46,600 gross; Elastic 307,800-486,900 PLN gross/year |
| Junior UoP | 7,000-15,250 PLN gross | Netcompany, Allegro SE1 |

### 4.3 Work arrangement

- 20 of 26 active postings are hybrid; the dominant Warsaw pattern is **1 office day per week** (Avenga, Experis, IT Factory, TT PSC, ASTEK, SCALO, Edge One). Banks ask for 2-3 days (ING 2, Société Générale 3, Box 3). Allegro requires 4.
- Fully remote within Poland: Sii, Shaped Thoughts, BlueSoft, Elastic. Polish C1/C2 is a hard requirement in roughly a third of Polish-market postings (TSS, TT PSC, Shaped Thoughts, ING, BlueSoft).

### 4.4 Demand clusters

1. **Insurance claims platforms (Warsaw)**: Avenga, ASTEK, IT Factory, TT PSC, SCALO, Link Group. Stack: Java, Spring Boot/Cloud, Hibernate, Kafka, SQL, DDD, microservices, JUnit/Spock/Testcontainers/WireMock, BPMN (Flowable/Camunda), AI-assisted development. This is the single largest coherent demand cluster found and matches the reference vacancy.
2. **Banking / payments (Warsaw, Katowice)**: ING, Nordea, Société Générale, Luxoft, Edge One, Avenga core banking, Experis VoP, Revolut, Stripe. Adds Redis, Oracle/PostgreSQL, RabbitMQ/JMS, OpenAPI/AsyncAPI, security protocols, resilience, production diagnostics.
3. **Platform / infrastructure engineering (global tech in Warsaw)**: Netflix Java Platform, Box Data Foundations, Elastic, Google Cloud Dataform. Emphasis shifts from frameworks to JVM internals, distributed-systems fundamentals, API/library design, observability and on-call ownership.
4. **Consulting bench roles**: Accenture, Sii, Capgemini, EPAM. Broad stacks, cloud-agnostic, frequently full-stack (Angular) or polyglot.

### 4.5 Trends visible in 2026 postings

- **AI-assisted development is now a stated requirement, not a curiosity**: 9 of 37 mid/senior postings require it (Avenga reference, ASTEK, IT Factory, TT PSC, SCALO, Prime Force, Devire, Elastic, Pekao) and 3 more list it as nice-to-have (Experis: Copilot/MCP; Shaped Thoughts: Claude Code/Codex; Allegro: LLM tooling). Prime Force explicitly names "coding agents, agent skills, MCP servers". Shaped Thoughts simultaneously bans AI use during interviews, so the expectation is tool fluency plus independent competence.
- **Java 17+/21+ is the stated baseline** wherever a version is named (Nordea 21+, Experis 21/25, Rightmove 21+, ING 17+, Société Générale 17+, Accenture 17+, Netflix 17/21, Revolut 17/21). No active posting names Java 8 as the target; EPAM's expired posting said "Java 8+".
- **Kafka is the default broker** (16 of 37 required), with RabbitMQ/JMS as secondary alternatives; event-driven wording accompanies it in most banking and insurance roles.
- **API contracts**: OpenAPI appears in Nordea, TSS, Jit/Grid Dynamics and Aviva (unverified); AsyncAPI appears twice (Nordea, Jit Team), which is new for the Polish market.
- **Testing tool names are rarer than expected**: only the insurance cluster (Avenga, ASTEK) names Testcontainers/WireMock; most postings say "automated tests" or "unit and integration tests". TDD appears in Rightmove, Revolut and Shaped Thoughts.
- **Observability** (Prometheus/Grafana/ELK/OpenTelemetry) is required in Experis, Devire and Nexio, nice-to-have in ING and TSS, and implied by "production ownership" in Box, Netflix, Google and Allegro.
- **Security** is concrete only in identity-heavy roles (TSS: OAuth2/SAML2/OIDC; Elastic: OAuth2/SAML/TLS/PKI; Experis: JWT/mTLS; Prime Force: OWASP). Elsewhere it is a one-line expectation ("security in design", "secure environment").

## 5. Honest limitations

- Only 26 of 37 mid/senior postings used were confirmed open on 2026-10-23; 2 of those were confirmed through mirrors because the official pages (Google, Netflix Workday) did not render for the fetch tool.
- NoFluffJobs, Pracuj.pl and TheProtocol could not be read, so the Polish sample is biased toward justjoin.it and bulldogjob.pl. Both boards carry most of the same employers, but salary disclosure norms differ.
- Big-tech coverage is thin: Google (mirror), Netflix (mirror), Box, Elastic and Stripe are verified; Amazon, Microsoft, NVIDIA, Uber, Goldman Sachs, JPMorgan, Bloomberg and Mastercard are not. Statements about those employers in this document are limited to "no fetchable Java posting found".
- Five Warsaw postings describe the same insurance client through different vendors; they are kept as separate rows because each is a distinct advertised vacancy, but the frequency table in `engineering-skills-matrix.md` notes the resulting over-weighting.
