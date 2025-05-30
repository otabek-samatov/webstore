ALTER TABLE product_category
    ADD CONSTRAINT uc_product_category_name UNIQUE (name);

ALTER TABLE publisher_company
    ADD CONSTRAINT uc_publisher_company_name UNIQUE (name);