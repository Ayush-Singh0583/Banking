# BONUS: interview traps and extra points

Short extra points from the lessons. Each one is the kind of thing an interviewer uses to check whether you *really* understand the topic.

---

## Money and BigDecimal
- **Why not `double`?** `double` stores numbers in binary and can't hold 0.1 exactly. Try `jshell` → `0.1 + 0.2` → `0.30000000000000004`. Small errors add up over millions of transactions.
- **Trap: `new BigDecimal(0.1)`** is already wrong, because `0.1` is a double. Use **`new BigDecimal("0.1")`** (a String).
- **Trap: `equals` vs `compareTo`.** `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` is **false**, because `equals` also checks the number of decimal places. Use **`a.compareTo(b) == 0`**.
- **Trap: BigDecimal can't be changed in place.** `balance.subtract(amount);` on its own does **nothing**. It returns a *new* number, so you must assign it back: `balance = balance.subtract(amount);`
- You can't use `+` or `-` with BigDecimal. Use `.add()` and `.subtract()`.
- **Trap: `scale = 2` rounds silently.** `@Positive` still allows ₹0.005. Ayush 999.995 is saved as 1000.00, and Riya 500.005 is saved as 500.01, so 1 paisa is created from nothing. *(From the code review. Check it yourself with curl first.)* Fix: `@Digits(integer = 17, fraction = 2)` on the DTO's `amount`.

## The Account entity
- **`Long id`, not `int`:** `null` clearly means "not saved yet" (0 looks like a real id), and `int` runs out at about 2.1 billion.
  - *Correction from a lesson:* Spring Data *does* treat `0` as "new" for an `int` id. Don't claim that `int` breaks it.
- **`protected Account() {}`:** JPA needs an empty constructor to build objects from table rows. `protected` stops other code from creating an account with no name and no balance.
- **No `setBalance()`:** otherwise any code could set any balance (₹1000 billion, a negative number...). The balance only changes through `debit()` and `credit()`, the one place where rules *should* be checked. **v1 gap:** they don't check anything yet, so `debit(-500)` works for any caller that skips the DTO. Next step: make them reject amounts ≤ 0.

## Java gotchas
- **Trap: `==` on `Long`.** `Long` values are *objects*, so `==` checks "same object?", not "same number?". It happens to work for small numbers (-128 to 127) and **breaks above 127**. Use `.equals()`.
- `x ? a : b` means "if x then a, else b", a one-line if/else.
- `super(message)` passes the message up to `RuntimeException`, so `e.getMessage()` returns it later.
- `Optional<Account>` is a **box that might be empty**. `.orElseThrow(...)` means "give me what's inside, or throw if it's empty".
- `() -> new SomeException(...)` is a **lambda**: a small piece of code that only runs if it's needed.
- `Math.min(a, b)` and `Math.max(a, b)` return the smaller and the bigger value.

## Imports (Spring Boot 3+ / 4)
- Boot 3+ uses **`jakarta`**, not `javax`.
- `LockModeType` comes from `jakarta.persistence` (standard JPA). `@Lock`, `@Query`, `@Param` and `JpaRepository` come from `org.springframework.data`.
- **Trap:** for `@Transactional`, import **`org.springframework.transaction.annotation.Transactional`**, not `jakarta.transaction.Transactional`.
- In Spring Boot 4, the web starter is called `spring-boot-starter-webmvc`.
- VS Code: put the cursor on a red word and press **Ctrl+.** → it offers to add the import.

## Dependency injection
- **Constructor injection:** the field can be `final`, you can't forget a dependency, and tests are easy.
- **Testing:** `AccountService service = new AccountService(fakeRepo);`. No Spring needed, and nothing to extend.
- **Field injection (`@Autowired private ...`):** when Spring isn't running, nothing fills the field, so it stays `null` → NullPointerException.

## Repositories and queries
- **JPQL** uses the **Java class name** (`Account`), not the table name.
- **`:id`** is a blank in the query, and **`@Param("id")`** says "fill that blank with this method's `id`".
- **Bonus:** Spring fills the blank *safely*, so a hacker can't sneak SQL in through the id. This is **SQL injection protection**.
- **Why a separate `findByIdForUpdate` instead of putting `@Lock` on `findById`?** Then *every* read would lock the row, even a simple "show my balance". A separate method makes it clear when you're locking.

## Pessimistic lock
- `PESSIMISTIC_WRITE` makes the database run `SELECT ... FOR UPDATE`. That row is locked, and anyone else who tries to lock it **waits**.
- It's called *pessimistic* because it assumes a clash *will* happen, so it locks first.
- **The lock lasts until the transaction ends.** Without `@Transactional` it's useless, and Hibernate may even refuse to run the query.
- **Lock vs idempotency key:**
  - Lock: two *different* requests on the same row take turns, so no update is lost.
  - Idempotency key: stops the *same* request from running twice (a double-tap).
  - **Trap:** with ₹500, a double-tap of "Pay ₹50" goes through **twice** even with the lock. Only an idempotency key stops it.

## @Transactional
- **In one line:** the code runs line by line, but the database only *keeps* the changes if the whole method finishes.
- **Trap: only a RuntimeException rolls back by default.** A checked exception (`extends Exception`) still **commits**. That's why `InsufficientBalanceException extends RuntimeException`.
- **Power cut halfway through?** Nothing was committed, so after a restart the balance is unchanged.
- **Self-invocation trap:** Spring puts a **proxy** (a middleman) in front of your class. Calls from *another* bean go through the proxy, so the transaction starts. A call from *inside the same class* (`this.transfer()`) skips the proxy, so there's no transaction.
  - **Fix:** put `@Transactional` on the outer method too, or move the outer method to a different bean.
- **No `save()` needed:** if you change an entity you loaded inside a transaction, Hibernate saves it automatically at commit. This is called "dirty checking".
- `AccountService.getForUpdate()` has no `@Transactional` because it runs *during* `transfer()`, so it's inside the same transaction, and its locks last until `transfer()` ends.

## Deadlocks and lock order
- **Deadlock:** transfer 1→2 locks account 1 and waits for 2, while transfer 2→1 locks account 2 and waits for 1. Both wait forever.
- **Fix:** always lock the **smaller id first**. Both transfers then try account 1 first, and one simply waits its turn.
- **Lock order ≠ money direction.** If account 9 sends to account 4, account 4 is locked first, but **9 still loses the money**.
- This is real-world practice. Any fixed order works; sorting by id is the simplest.

## Modular monolith
- **Monolith:** one program and one database, like one restaurant with one kitchen.
- **Microservices:** many small programs, each with its own database, like the stalls in a food court.
- **Modular monolith:** one building, but with separate **rooms** (packages) and a **counter** at each door (the service).
- **The rule:** a module only calls another module's **service**, never its repository. *You never get the repository itself. You ask the other module's service, and it uses its own repository for you.*
- **Inside its own module**, a service *does* use its own repository. That's its job: the teller is allowed into the vault.
- **Why a monolith for transfers?** One database means one `@Transactional` gives you all-or-nothing for free. With microservices and two databases, you'd need extra "undo" code (like a refund) if the second step fails.
- **When it's the wrong choice:** huge teams getting in each other's way, or one part needing far more servers than the rest. Many companies start as a modular monolith and split later.

## Controllers and DTOs (Module 4)
- **Never accept the entity in a request.** A client could send `"balance": 999999`. Use a **DTO**, a small class that describes only the request's shape.
- A Java **`record`** is a good DTO. Note that its getters are named `fromId()`, **not** `getFromId()`.
- **`@Valid`** on the controller parameter checks annotations like `@NotNull` and `@Positive`. Bad input gets a **400** before your code even runs.
- **Security hole:** without `@Positive`, `transfer(1, 2, -500)` passes the balance check, *adds* ₹500 to the sender and *takes* ₹500 from the receiver. That's theft.
- **`@RestControllerAdvice`:** one class that turns exceptions from every controller into clean HTTP responses instead of an ugly 500.
- **Trap: `@Controller` vs `@RestController`.** `@Controller` is for websites that return HTML pages: return a plain `String` and Spring looks for a page with that name. `@RestController` sends your data straight back.
- **Trap: forgetting `@Valid`.** Without it, `@NotNull` and `@Positive` are silently **ignored**. Remove it, and a hacker can send `-500` with curl: Ayush 750 → **1250**, Riya 750 → **250**.
- **Never trust the client.** "The mobile app already checks it" doesn't count, because anyone can skip the app and call your API directly with curl. Always validate on the server.
- **Status codes:**
  - **200** = OK
  - **400** = your input is wrong (failed validation)
  - **404** = that thing doesn't exist
  - **409** = valid request, but it conflicts with the current state (balance too low)
  - **500** = the server broke, so a 500 for a *client* mistake is a bug
- **Currently a known gap:** a transfer to an account that doesn't exist returns **500**, because nothing handles that exception. The fix is an `AccountNotFoundException` plus a handler → **404**.
- **Where does the 409 message come from?** `throw new InsufficientBalanceException("...")` → `super(message)` → `e.getMessage()` in the handler → the response body.
- **`curl -i`** shows the status code and headers. `Content-Length` is the size of the body in **bytes**, not characters. `Transfer successful` is 19 of both, but `₹` alone is 3 bytes.
- **VS Code tip:** if a suggestion list pops up while you're typing an annotation, press **Esc**. Pressing Enter can insert a whole snippet method.

## Test data and the H2 console
- `src/main/resources/data.sql` runs on every start. Because H2 is in memory, the data resets each time.
- `spring.jpa.defer-datasource-initialization=true` makes `data.sql` run **after** Hibernate creates the tables. Otherwise the INSERTs fail with "table not found".
- H2 console: `http://localhost:8080/h2-console` → JDBC URL `jdbc:h2:mem:bankdb`, user `sa`, empty password.

---

## Interview lines (say these in your own words)
- **Modular monolith:** "I chose a modular monolith because a transfer has to debit and credit together. With one database, a single `@Transactional` gives me that. The modules keep the boundaries clean, so I could split it into services later."
- **Module boundary:** "`TransferService` doesn't ask for `AccountRepository`. It asks `AccountService.getForUpdate(id)` for a locked account, and `AccountService` handles the repository."
- **Honesty:** if the project isn't finished yet, say exactly which parts are done and which are next. Never claim more than you built.
- **Use your own words**, not AI summaries. Interviewers notice words like "orchestrates" and "deterministic".
