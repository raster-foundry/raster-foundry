CREATE TABLE last_unlocked (
    unlocked_time timestamp with time zone not null primary key
);

INSERT INTO
    last_unlocked (unlocked_time)
values
    (now());