CREATE TABLE IF NOT EXISTS item_price_update_blacklist (
    id BIGSERIAL PRIMARY KEY,
    list_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_item_price_update_blacklist_list_item
        UNIQUE (list_id, item_id),
    CONSTRAINT fk_item_price_update_blacklist_list
        FOREIGN KEY (list_id) REFERENCES recipe_lists(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_item_price_update_blacklist_item
        FOREIGN KEY (item_id) REFERENCES items(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_item_price_update_blacklist_list_id
    ON item_price_update_blacklist (list_id);
CREATE INDEX IF NOT EXISTS idx_item_price_update_blacklist_item_id
    ON item_price_update_blacklist (item_id);
