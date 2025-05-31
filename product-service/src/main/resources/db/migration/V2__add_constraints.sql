ALTER TABLE bookauthorrelation
    ADD CONSTRAINT uc_bookauthorrelation_bookid UNIQUE (bookid, bookauthorid);

ALTER TABLE bookcategoryrelation
    ADD CONSTRAINT uc_bookcategoryrelation UNIQUE (bookid, productcategoryid);