CREATE SEQUENCE IF NOT EXISTS author_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS book_author_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS book_category_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS book_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS category_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS publisher_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE Publisher
(
    id      BIGINT       NOT NULL,
    version INTEGER,
    name    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_publisher PRIMARY KEY (id),
    CONSTRAINT uc_publisher_name UNIQUE (name)
);

CREATE TABLE Author
(
    id          BIGINT       NOT NULL,
    version     INTEGER,
    firstName  VARCHAR(255),
    middleName VARCHAR(255),
    lastName   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_author PRIMARY KEY (id)
);

CREATE TABLE Category
(
    id        BIGINT       NOT NULL,
    version   INTEGER,
    name      VARCHAR(255) NOT NULL,
    parentId BIGINT,
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uc_category_name UNIQUE (name),
    FOREIGN KEY (parentId) REFERENCES category (id) ON DELETE CASCADE
);

CREATE TABLE Book
(
    id               INTEGER       NOT NULL,
    version          BIGINT,
    title            VARCHAR(255)  NOT NULL,
    subtitle         VARCHAR(255),
    publisherId     BIGINT        NOT NULL,
    publicationDate date          NOT NULL,
    isbn             VARCHAR(255)  NOT NULL,
    description      VARCHAR(2000),
    price            DECIMAL(6, 2) NOT NULL,
    language         VARCHAR(255)  NOT NULL,
    CONSTRAINT pk_book PRIMARY KEY (id),
    CONSTRAINT uc_book_isbn UNIQUE (isbn),
    FOREIGN KEY (publisherId) REFERENCES publisher (id) ON DELETE CASCADE
);

CREATE TABLE BookAuthor
(
    id        BIGINT NOT NULL,
    bookId   INTEGER,
    authorId BIGINT,
    CONSTRAINT pk_bookauthor PRIMARY KEY (id),
    CONSTRAINT uc_bookauthor_bookid_authorid UNIQUE (bookId, authorId),
    FOREIGN KEY (authorId) REFERENCES author (id) ON DELETE CASCADE,
    FOREIGN KEY (bookId) REFERENCES book (id) ON DELETE CASCADE
);

CREATE TABLE BookCategory
(
    id          BIGINT NOT NULL,
    bookId     INTEGER,
    categoryId BIGINT,
    CONSTRAINT pk_bookcategory PRIMARY KEY (id),
    CONSTRAINT uc_bookcategory_bookid UNIQUE (bookId, categoryId),
    FOREIGN KEY (bookId) REFERENCES book (id) ON DELETE CASCADE,
    FOREIGN KEY (categoryId) REFERENCES category (id) ON DELETE CASCADE
);

CREATE TABLE BookImages
(
    bookId    INTEGER NOT NULL,
    imageUrl VARCHAR(255),
    FOREIGN KEY (bookid) REFERENCES book (id) ON DELETE CASCADE
);

CREATE INDEX idx_book_title ON Book (title);

CREATE INDEX idx_category_name ON Category (name);

CREATE INDEX idx_lastName ON Author (lastName);

CREATE INDEX idx_publisher_name ON publisher (name);
