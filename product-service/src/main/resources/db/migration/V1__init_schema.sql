CREATE SEQUENCE IF NOT EXISTS author_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS book_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS category_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS publisher_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE Author
(
    id         BIGINT       NOT NULL,
    version    INTEGER,
    firstName  VARCHAR(255),
    middleName VARCHAR(255),
    lastName   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_author PRIMARY KEY (id)
);

CREATE TABLE Book
(
    id              BIGINT        NOT NULL,
    version         BIGINT,
    title           VARCHAR(255)  NOT NULL,
    subtitle        VARCHAR(255),
    publisherId     BIGINT        NOT NULL,
    publicationDate date          NOT NULL,
    isbn            VARCHAR(255)  NOT NULL,
    description     VARCHAR(2000),
    price           DECIMAL(6, 2) NOT NULL,
    language        VARCHAR(255)  NOT NULL,
    CONSTRAINT pk_book PRIMARY KEY (id)
);

CREATE TABLE book_author
(
    authorId BIGINT NOT NULL,
    bookId   BIGINT NOT NULL,
    CONSTRAINT pk_book_author PRIMARY KEY (authorId, bookId)
);

CREATE TABLE book_category
(
    bookId    BIGINT NOT NULL,
    categoryId BIGINT NOT NULL,
    CONSTRAINT pk_book_category PRIMARY KEY (bookId, categoryId)
);

CREATE TABLE book_images
(
    bookId   BIGINT NOT NULL,
    imageUrl VARCHAR(255)
);

CREATE TABLE Category
(
    id       BIGINT       NOT NULL,
    version  INTEGER,
    name     VARCHAR(255) NOT NULL,
    parentId BIGINT,
    CONSTRAINT pk_category PRIMARY KEY (id)
);

CREATE TABLE publisher
(
    id      BIGINT       NOT NULL,
    version INTEGER,
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

CREATE INDEX idx_lastName ON author (lastName);

CREATE INDEX idx_publisher_name ON publisher (name);

ALTER TABLE book
    ADD CONSTRAINT FK_BOOK_ON_PUBLISHERID FOREIGN KEY (publisherId) REFERENCES publisher (id);

ALTER TABLE category
    ADD CONSTRAINT FK_CATEGORY_ON_PARENTID FOREIGN KEY (parentId) REFERENCES category (id);

ALTER TABLE book_author
    ADD CONSTRAINT fk_bookaut_on_author FOREIGN KEY (authorId) REFERENCES author (id) ON DELETE CASCADE;

ALTER TABLE book_author
    ADD CONSTRAINT fk_book_aut_on_book FOREIGN KEY (bookId) REFERENCES book (id) ON DELETE CASCADE;

ALTER TABLE book_category
    ADD CONSTRAINT fk_book_cat_on_book FOREIGN KEY (bookId) REFERENCES book (id) ON DELETE CASCADE;

ALTER TABLE book_category
    ADD CONSTRAINT fk_book_cat_on_category FOREIGN KEY (categoryId) REFERENCES category (id) ON DELETE CASCADE;

ALTER TABLE book_images
    ADD CONSTRAINT fk_book_images_on_book FOREIGN KEY (bookid) REFERENCES book (id) ON DELETE CASCADE;