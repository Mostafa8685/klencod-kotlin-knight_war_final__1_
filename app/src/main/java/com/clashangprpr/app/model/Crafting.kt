package com.clashangprpr.app.model

import android.graphics.Color

data class CraftRecipe(
    val id: String,
    val titleAr: String,
    val descAr: String,
    val woodCost: Int,
    val stoneCost: Int,
    val ironCost: Int,
    val resultType: String,
    val iconAsset: String,
    val tint: Int? = null,
    val repeatable: Boolean = false
)

object CraftingRegistry {
    val recipes = listOf(
        CraftRecipe(
            "craft_pickaxe", "فأس التنقيب", "أداة لحفر الرمال واستخراج الكنوز المدفونة.",
            3, 2, 2, "PICKAXE", "ui/ico/axe.png", Color.parseColor("#FFB74D")
        ),
        CraftRecipe(
            "craft_bomb", "قنبلتان (x2)", "تنفجر بمساحة 140 وتضرر كل الأعداء حولها.",
            2, 1, 1, "BOMB", "icons/bomb.png", null, true
        ),
        CraftRecipe(
            "craft_plasma", "مدفع البلازما", "السحر يصبح ليزر ضرره 120 ويخترق الأعداء.",
            4, 3, 5, "PLASMA", "icons/cannon.png"
        ),
        CraftRecipe(
            "craft_shield", "درع النانو", "+150 صحة قصوى ويقلل الضرر 40%.",
            3, 4, 5, "SHIELD", "ui/ico/shield.png", Color.parseColor("#4DD0E1")
        ),
        CraftRecipe(
            "craft_godblade", "نصل الأبعاد الكوني", "أقوى سلاح: ضرر 160، والسحر يصبح 160 ويخترق.",
            5, 5, 6, "GODBLADE", "icons/blade.png"
        )
    )
}
