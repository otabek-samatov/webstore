CREATE SEQUENCE IF NOT EXISTS author_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS book_author_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS book_category_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS book_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS category_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS publisher_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE Author
(
    id          BIGINT       NOT NULL,
    version     INTEGER,
    firstName  VARCHAR(255),
    middleName VARCHAR(255),
    lastName   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_author PRIMARY KEY (id)
);

CREATE TABLE Book
(
    id               BIGINT        NOT NULL,
    version          BIGINT,
    title            VARCHAR(255)  NOT NULL,
    subtitle         VARCHAR(255),
    publisherId     BIGINT        NOT NULL,
    publicationDate date          NOT NULL,
    isbn             VARCHAR(255)  NOT NULL,
    description      VARCHAR(2000),
    price            DECIMAL(6, 2) NOT NULL,
    language         VARCHAR(255)  NOT NULL,
    CONSTRAINT pk_book PRIMARY KEY (id)
);

CREATE TABLE BookAuthor
(
    id        BIGINT NOT NULL,
    bookId   BIGINT NOT NULL,
    authorId BIGINT NOT NULL,
    CONSTRAINT pk_bookauthor PRIMARY KEY (id)
);

CREATE TABLE BookCategory
(
    id          BIGINT NOT NULL,
    bookId     BIGINT NOT NULL,
    categoryId BIGINT NOT NULL,
    CONSTRAINT pk_bookcategory PRIMARY KEY (id)
);

CREATE TABLE BookImages
(
    bookId    BIGINT NOT NULL,
    imageUrl VARCHAR(255)
);

CREATE TABLE Category
(
    id        BIGINT       NOT NULL,
    version   INTEGER,
    name      VARCHAR(255) NOT NULL,
    parentId BIGINT,
    CONSTRAINT pk_category PRIMARY KEY (id)
);

CREATE TABLE Publisher
(
    id      BIGINT       NOT NULL,
    version INTEGER,
    name    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_publisher PRIMARY KEY (id)
);

ALTER TABLE Book
    ADD CONSTRAINT uc_book_isbn UNIQUE (isbn);

ALTER TABLE BookAuthor
    ADD CONSTRAINT uc_bookauthor_bookid_authorid UNIQUE (bookId, authorId);

ALTER TABLE BookCategory
    ADD CONSTRAINT uc_bookcategory_bookid UNIQUE (bookId, categoryId);

ALTER TABLE Category
    ADD CONSTRAINT uc_category_name UNIQUE (name);

ALTER TABLE publisher
    ADD CONSTRAINT uc_publisher_name UNIQUE (name);

CREATE INDEX idx_book_title ON book (title);

CREATE INDEX idx_category_name ON category (name);

CREATE INDEX idx_lastName ON author (lastName);

CREATE INDEX idx_publisher_name ON publisher (name);

ALTER TABLE BookAuthor
    ADD CONSTRAINT FK_BOOKAUTHOR_ON_AUTHORID FOREIGN KEY (authorId) REFERENCES author (id) ON DELETE CASCADE;

ALTER TABLE BookAuthor
    ADD CONSTRAINT FK_BOOKAUTHOR_ON_BOOKID FOREIGN KEY (bookId) REFERENCES book (id) ON DELETE CASCADE;

ALTER TABLE BookCategory
    ADD CONSTRAINT FK_BOOKCATEGORY_ON_BOOKID FOREIGN KEY (bookId) REFERENCES book (id) ON DELETE CASCADE;

ALTER TABLE BookCategory
    ADD CONSTRAINT FK_BOOKCATEGORY_ON_CATEGORYID FOREIGN KEY (categoryId) REFERENCES category (id) ON DELETE CASCADE;

ALTER TABLE Book
    ADD CONSTRAINT FK_BOOK_ON_PUBLISHERID FOREIGN KEY (publisherId) REFERENCES publisher (id);

ALTER TABLE Category
    ADD CONSTRAINT FK_CATEGORY_ON_PARENTID FOREIGN KEY (parentId) REFERENCES category (id);

ALTER TABLE BookImages
    ADD CONSTRAINT fk_bookimages_on_book FOREIGN KEY (bookid) REFERENCES book (id);