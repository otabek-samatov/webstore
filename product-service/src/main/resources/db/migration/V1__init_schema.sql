CREATE SEQUENCE IF NOT EXISTS book_author_relation_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS book_author_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS book_category_relation_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS book_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS product_category_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS publisher_company_seq START WITH 1 INCREMENT BY 1;

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

CREATE TABLE BookAuthor
(
    id         BIGINT       NOT NULL,
    version    BIGINT,
    firstName  VARCHAR(255),
    middleName VARCHAR(255),
    lastName   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_bookauthor PRIMARY KEY (id)
);

CREATE TABLE BookAuthorRelation
(
    id           BIGINT NOT NULL,
    version      BIGINT,
    bookId       BIGINT,
    bookAuthorId BIGINT,
    CONSTRAINT pk_bookauthorrelation PRIMARY KEY (id)
);

CREATE TABLE BookCategoryRelation
(
    id                  BIGINT NOT NULL,
    version             BIGINT,
    bookId             BIGINT,
    productCategoryId BIGINT,
    CONSTRAINT pk_book_category_relation PRIMARY KEY (id)
);

CREATE TABLE BookImages
(
    bookId   BIGINT NOT NULL,
    imageUrl VARCHAR(255)
);

CREATE TABLE ProductCategory
(
    id        BIGINT       NOT NULL,
    version   BIGINT,
    name      VARCHAR(255) NOT NULL,
    parentId BIGINT,
    CONSTRAINT pk_productcategory PRIMARY KEY (id)
);

CREATE TABLE PublisherCompany
(
    id      BIGINT       NOT NULL,
    version BIGINT,
    name    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_publishercompany PRIMARY KEY (id)
);

ALTER TABLE Book
    ADD CONSTRAINT uc_book_isbn UNIQUE (isbn);

ALTER TABLE ProductCategory
    ADD CONSTRAINT uc_productcategory_name UNIQUE (name);

ALTER TABLE PublisherCompany
    ADD CONSTRAINT uc_publishercompany_name UNIQUE (name);

CREATE INDEX idx_book_title ON Book (title);

CREATE INDEX idx_bookauthorrelation_bookid ON BookAuthorRelation (bookId, bookAuthorId);

CREATE INDEX idx_bookcategoryrelation ON BookCategoryRelation (bookId, productCategoryId);

CREATE INDEX idx_lastName ON BookAuthor (lastName);

CREATE INDEX idx_productcategory_name ON ProductCategory (name);

CREATE INDEX idx_publishercompany_name ON PublisherCompany (name);

ALTER TABLE BookAuthorRelation
    ADD CONSTRAINT FK_BOOKAUTHORRELATION_ON_BOOKAUTHORID FOREIGN KEY (bookAuthorId) REFERENCES BookAuthor (id);

ALTER TABLE BookAuthorRelation
    ADD CONSTRAINT FK_BOOKAUTHORRELATION_ON_BOOKID FOREIGN KEY (bookId) REFERENCES book (id);

ALTER TABLE BookCategoryRelation
    ADD CONSTRAINT FK_BOOK_CATEGORY_RELATION_ON_BOOKID FOREIGN KEY (bookId) REFERENCES book (id);

ALTER TABLE BookCategoryRelation
    ADD CONSTRAINT FK_BOOK_CATEGORY_RELATION_ON_PRODUCTCATEGORYID FOREIGN KEY (productCategoryId) REFERENCES ProductCategory (id);

ALTER TABLE Book
    ADD CONSTRAINT FK_BOOK_ON_PUBLISHERID FOREIGN KEY (publisherId) REFERENCES PublisherCompany (id);

ALTER TABLE ProductCategory
    ADD CONSTRAINT FK_PRODUCTCATEGORY_ON_PARENTID FOREIGN KEY (parentId) REFERENCES ProductCategory (id);

ALTER TABLE BookImages
    ADD CONSTRAINT fk_bookimages_on_book FOREIGN KEY (bookId) REFERENCES book (id);