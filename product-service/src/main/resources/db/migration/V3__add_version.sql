ALTER TABLE book
    ADD version BIGINT;

ALTER TABLE book_author
    ADD version BIGINT;

ALTER TABLE product_category
    ADD version BIGINT;

ALTER TABLE publisher_company
    ADD version BIGINT;