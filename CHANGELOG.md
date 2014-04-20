# Changelog

## 1.5

- [custom ids](/docs/entity.md#custom-id)
- [mysql async](/docs/persistence-context.md)
- [reads without transaction](/docs/transaction.md#transaction)
- [new play integration](/docs/play-framework.md#activateplaycontext)
- [test support](/docs/test-support.md)
- [deferred read validation](/docs/multiple-vms.md#deferred-read-validation)
- [query collection operators (in/notInt)](/docs/query.md#collection-operators)
- [custom classpath scan](/docs/persistence-context.md)
- [persisted index](/docs/persistence-context.md#persisted-index)
- [zip encoder](/docs/entity.md#custom-type-encoders)
- [bug fixes](https://github.com/fwbrasil/activate/issues?milestone=4&page=1&state=closed)

## 1.4

- Prevalent storage
- Slick 2 lifted embedding support
- Sql Server dialect
- Custom type encoders
- Lift Framewok integration
- Update to Play 2.2
- Custom cache configuration
- Eager queries
- Cached queries
- Dynamic queries
- Memory index
- Lifecycle listeners
- Property listeners
- EntityMap
- Cascade delete
- Kryo serializer
- Modify column type migration
- Queries with empty where

## 1.3

- Async transactions
- Async queries
- Reactive PostgreSQL storage (using postgresql-async)
- Reactive MongoDB storage (using reactivemongo)
- Slick direct embedding queries
- Spray json integration
- Lazy entity lists
- Limited queries offset
- toUpperCase/toLowerCase query functions

## 1.2

- Scala 2.10
- Polyglot persistence
- Optimistic offline locking
- Play 2.1 support
- DB2 jdbc dialect
- Limited queries
- Better transient field support
- Custom serializers
- Nested list order preservation


## 1.1

- Play application hot reload support (ActivatePlayPlugin)
- New query syntax option: select[MyEntity] where(_.name :== “a”)
- New databases: h2, derby and hsqldb.
- Support for list fields in entities: class MyEntity(var intList: List[Int]) extends Entity
- @Alias annotation to customize entity and field names at the storage
- Manual migrations
- Multiple VMs support (Coordinator)
- Performance enhancements

## 1.0

- Migrations
- Play Framework support
- Mass update/delete statements
- Validation using Design By Contract
- Queries with “order by”
- Paginated queries
- Storage direct access
- System properties defined storage
- Better error messages
- It’s not necessary to use “-noverify” anymore
- Performance enhancements