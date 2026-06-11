# Email System

A secure Spring Boot email system for composing, sending, receiving, and auditing email messages.

## Stack

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- JavaMail through Spring Boot Mail
- MySQL
- Thymeleaf

## Features

- User registration and login
- Compose and send email through SMTP
- Receive email through POP3/POP3S
- Securely store sent and received email history

## Local Setup

Create a MySQL database:

```sql
CREATE DATABASE email_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Set environment variables before running:

```bash
export DB_USERNAME=root
export DB_PASSWORD=your_mysql_password
export MAIL_HOST=smtp.gmail.com
export MAIL_PORT=587
export MAIL_USERNAME=your_email@gmail.com
export MAIL_PASSWORD=your_gmail_app_password
export POP3_HOST=pop.gmail.com
export POP3_PORT=995
export POP3_SSL=true
export POP3_USERNAME=your_email@gmail.com
export POP3_PASSWORD=your_gmail_app_password
export POP3_MAX_MESSAGES=10
```

Then run:

```bash
mvn spring-boot:run
```

If SMTP credentials are not configured, the app still stores outbound messages in history and marks them as `DRAFT`, which is useful for development.

## Run With Docker

Start MySQL and the Spring Boot app together:

```bash
docker compose up --build
```

Then open:

```text
http://localhost:8080/register
```

To stop everything:

```bash
docker compose down
```

To remove the MySQL data volume and start fresh:

```bash
docker compose down -v
```

Create an account from `/register`, then sign in from `/login`.
