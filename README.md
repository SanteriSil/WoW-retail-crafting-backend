# WoW Retail Crafting Backend

Spring Boot backend for storing WoW crafting items, recipes, and item price history.

## Run

- `./gradlew bootRun`
- Default base URL: `http://localhost:8080`

## Authentication status

Admin endpoints under `/admin/**` are intended to be JWT-protected (external issuer), but **security is not enforced yet**. See `src/main/java/com/crafting/config/SecurityConfig.java`.

## Endpoints

### Health

- `GET /health`
  - Returns: `{ "status": "UP" }`

### Public (GET)

#### Items

- `GET /items`
  - Returns: `ItemDTO[]`
  - Fields:
    - `id` (number)
    - `name` (string)
    - `professionId` (number | null)
    - `professionName` (string | null)
    - `quality` (number | null)
    - `finishingIngredient` (boolean)
    - `currentPrice` (number | null) — copper
    - `currentPriceRecordedAt` (string | null) — ISO-8601 timestamp

Example:

```bash
curl -s http://localhost:8080/items | jq
```

#### Recipes

- `GET /recipes`
  - Returns: `RecipeDTO[]`
  - Fields:
    - `id` (number)
    - `name` (string)
    - `outputItemId` (number)
    - `outputItemName` (string)
    - `professionId` (number | null)
    - `professionName` (string | null)
    - `ingredientsJson` (string | null)
    - `outputQuantity` (number)

Example:

```bash
curl -s http://localhost:8080/recipes | jq
```

### Admin (CUD)

#### Items

- `POST /admin/items`
  - Creates an item. The `id` is the Blizzard item id.
  - Body:

```json
{
  "id": 190328,
  "name": "Awakened Earth",
  "professionId": 1,
  "quality": 2,
  "finishingIngredient": false
}
```

- `PUT /admin/items/{id}`
  - Updates an item.
  - Body:

```json
{
  "name": "Awakened Earth",
  "professionId": 1,
  "quality": 2,
  "finishingIngredient": false
}
```

- `DELETE /admin/items/{id}`
  - Deletes an item.

#### Recipes

- `POST /admin/recipes`
  - Creates a recipe.
  - Notes:
    - `outputItemId` must exist in the `items` table.
    - `outputQuantity` defaults to `1.0` if omitted, and must be `> 0`.
    - `ingredientsJson` is required and must be a JSON **array** of `{id, quantity}` objects, where `quantity` is a **positive integer**.
  - Body:

```json
{
  "name": "Example Recipe",
  "outputItemId": 190328,
  "professionId": 1,
  "outputQuantity": 1.0,
  "ingredientsJson": "[{\"id\":190311,\"quantity\":2},{\"id\":190312,\"quantity\":1}]"
}
```

- `PUT /admin/recipes/{id}`
  - Updates a recipe.
  - Body is the same shape as create.

- `DELETE /admin/recipes/{id}`
  - Deletes a recipe.

## Notes

- Prices are stored in **copper** (integer values).
- Flyway migrations live in `src/main/resources/db/migration/`.
