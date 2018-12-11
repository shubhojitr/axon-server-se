drop table application_role;

create table application_context (
  id       BIGINT generated by default as identity,
  context  VARCHAR(255),
  primary key (id)
);

create table application_context_role (
  id                     BIGINT generated by default as identity,
  application_context_id BIGINT not null,
  role                   VARCHAR(255),
  CONSTRAINT fk_application_context_id FOREIGN KEY (application_context_id) REFERENCES application_context (id)
);