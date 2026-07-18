/**
 * Core IR data model shared by all tiers.
 *
 * <p>Null-marked: references are non-null by default. Layout fields
 * ({@code Node.x/y/width/height}) and optional labels are explicitly
 * {@code @Nullable} — they are absent until Tier 2 runs.
 */
@NullMarked
package com.karta.core.model;

import org.jspecify.annotations.NullMarked;
