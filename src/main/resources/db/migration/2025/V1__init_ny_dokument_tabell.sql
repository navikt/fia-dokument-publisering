
CREATE TABLE dokument (
    dokument_id             varchar(36) not null primary key,
    referanse_id            varchar(36) not null,
    type                    varchar not null,
    opprettet_av            varchar not null,
    status                  varchar not null,
    orgnr                   varchar(20) not null,
    saksnummer              varchar(26) not null,
    samarbeid_id            int not null,
    samarbeid_navn          varchar not null,
    innhold                 jsonb not null,
    sendt_til_publisering   timestamp not null,
    opprettet               timestamp default current_timestamp,
    publisert               timestamp default null,
    sist_endret             timestamp default null
);