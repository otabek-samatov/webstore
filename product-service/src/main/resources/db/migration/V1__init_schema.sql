CREATE SEQUENCE IF NOT EXISTS book_author_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS book_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS product_category_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS publisher_company_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE book
(
    id                   BIGINT         NOT NULL,
    title                VARCHAR(255)   NOT NULL,
    subtitle             VARCHAR(255),
    publisher_company_id BIGINT         NOT NULL,
    publication_date     date           NOT NULL,
    isbn                 VARCHAR(255)   NOT NULL,
    description          VARCHAR(2000),
    price                DECIMAL(19, 2) NOT NULL,
    language             VARCHAR(255)   NOT NULL,
    CONSTRAINT pk_book PRIMARY KEY (id)
);

CREATE TABLE book_author
(
    id          BIGINT       NOT NULL,
    first_name  VARCHAR(255),
    middle_name VARCHAR(255),
    last_name   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_book_author PRIMARY KEY (id)
);

CREATE TABLE book_authors
(
    author_id BIGINT NOT NULL,
    book_id   BIGINT NOT NULL,
    CONSTRAINT pk_book_authors PRIMARY KEY (author_id, book_id)
);

CREATE TABLE book_categories
(
    book_id       BIGINT NOT NULL,
    categories_id BIGINT NOT NULL,
    CONSTRAINT pk_book_categories PRIMARY KEY (book_id, categories_id)
);

CREATE TABLE book_images
(
    book_id        BIGINT NOT NULL,
    book_image_url VARCHAR(255)
);

CREATE TABLE product_category
(
    id                 BIGINT       NOT NULL,
    name               VARCHAR(255) NOT NULL,
    parent_category_id BIGINT,
    CONSTRAINT pk_product_category PRIMARY KEY (id)
);

CREATE TABLE publisher_company
(
    id   BIGINT       NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_publisher_company PRIMARY KEY (id)
);

ALTER TABLE book
    ADD CONSTRAINT uc_book_isbn UNIQUE (isbn);

CREATE INDEX idx_book_isbn ON book (isbn);

CREATE INDEX idx_book_title ON book (title);

CREATE INDEX idx_bookauthor_last_name ON book_author (last_name);

ALTER TABLE book
    ADD CONSTRAINT FK_BOOK_ON_PUBLISHER_COMPANY FOREIGN KEY (publisher_company_id) REFERENCES publisher_company (id);

ALTER TABLE product_category
    ADD CONSTRAINT FK_PRODUCT_CATEGORY_ON_PARENT_CATEGORY FOREIGN KEY (parent_category_id) REFERENCES product_category (id);

ALTER TABLE book_authors
    ADD CONSTRAINT fk_booaut_on_book FOREIGN KEY (book_id) REFERENCES book (id);

ALTER TABLE book_authors
    ADD CONSTRAINT fk_booaut_on_book_author FOREIGN KEY (author_id) REFERENCES book_author (id);

ALTER TABLE book_categories
    ADD CONSTRAINT fk_boocat_on_book FOREIGN KEY (book_id) REFERENCES book (id);

ALTER TABLE book_categories
    ADD CONSTRAINT fk_boocat_on_product_category FOREIGN KEY (categories_id) REFERENCES product_category (id);

ALTER TABLE book_images
    ADD CONSTRAINT fk_book_images_on_book FOREIGN KEY (book_id) REFERENCES book (id);