CREATE SEQUENCE IF NOT EXISTS author_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS book_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS category_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS publisher_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE author
(
    id          BIGINT       NOT NULL,
    version     BIGINT,
    first_name  VARCHAR(255),
    middle_name VARCHAR(255),
    last_name   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_author PRIMARY KEY (id)
);

CREATE TABLE book
(
    id               BIGINT        NOT NULL,
    version          BIGINT,
    title            VARCHAR(255)  NOT NULL,
    subtitle         VARCHAR(255),
    publisher_id     BIGINT        NOT NULL,
    publication_date date          NOT NULL,
    isbn             VARCHAR(255)  NOT NULL,
    description      VARCHAR(2000),
    price            DECIMAL(6, 2) NOT NULL,
    language         VARCHAR(255)  NOT NULL,
    CONSTRAINT pk_book PRIMARY KEY (id)
);

CREATE TABLE book_author
(
    author_id BIGINT NOT NULL,
    book_id   BIGINT NOT NULL,
    CONSTRAINT pk_book_author PRIMARY KEY (author_id, book_id)
);

CREATE TABLE book_category
(
    book_id     BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT pk_book_category PRIMARY KEY (book_id, category_id)
);

CREATE TABLE book_images
(
    book_id   BIGINT NOT NULL,
    image_url VARCHAR(255)
);

CREATE TABLE category
(
    id        BIGINT       NOT NULL,
    version   BIGINT,
    name      VARCHAR(255) NOT NULL,
    parent_id BIGINT,
    CONSTRAINT pk_category PRIMARY KEY (id)
);

CREATE TABLE publisher
(
    id      BIGINT       NOT NULL,
    version BIGINT,
    name    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_publisher PRIMARY KEY (id)
);

ALTER TABLE book
    ADD CONSTRAINT uc_book_isbn UNIQUE (isbn);

ALTER TABLE category
    ADD CONSTRAINT uc_category_name UNIQUE (name);

ALTER TABLE publisher
    ADD CONSTRAINT uc_publisher_name UNIQUE (name);

CREATE INDEX idx_book_title ON book (title);

CREATE INDEX idx_category_name ON category (name);

CREATE INDEX idx_lastName ON author (last_name);

CREATE INDEX idx_publisher_name ON publisher (name);

ALTER TABLE book
    ADD CONSTRAINT FK_BOOK_ON_PUBLISHERID FOREIGN KEY (publisher_id) REFERENCES publisher (id);

ALTER TABLE category
    ADD CONSTRAINT FK_CATEGORY_ON_PARENTID FOREIGN KEY (parent_id) REFERENCES category (id);

ALTER TABLE book_author
    ADD CONSTRAINT fk_booaut_on_author FOREIGN KEY (author_id) REFERENCES author (id);

ALTER TABLE book_author
    ADD CONSTRAINT fk_booaut_on_book FOREIGN KEY (book_id) REFERENCES book (id);

ALTER TABLE book_category
    ADD CONSTRAINT fk_boocat_on_book FOREIGN KEY (book_id) REFERENCES book (id);

ALTER TABLE book_category
    ADD CONSTRAINT fk_boocat_on_category FOREIGN KEY (category_id) REFERENCES category (id);

ALTER TABLE book_images
    ADD CONSTRAINT fk_book_images_on_book FOREIGN KEY (book_id) REFERENCES book (id);