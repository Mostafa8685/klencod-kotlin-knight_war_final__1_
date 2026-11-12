package com.clashangprpr.app.model

import android.graphics.Color

data class Weapon(
    val id: String,
    val nameAr: String,
    val damage: Int,
    val reach: Float,
    val cooldown: Float,
    val iconAsset: String,
    val color: Int
)

data class Armor(
    val id: String,
    val nameAr: String,
    val hpBonus: Int,
    val reduction: Float
)

enum class ItemType { WEAPON, ARMOR, CONSUMABLE, UPGRADE }

data class ShopItem(
    val id: String,
    val titleAr: String,
    val descAr: String,
    val price: Int,
    val type: ItemType,
    val iconAsset: String,
    val tint: Int? = null,
    val maxCount: Int = 1
)

object GameCatalogue {
    val WEAPON_BASIC = Weapon("sword_basic", "سيف الفجر", 35, 84f, 0.30f, "icons/item_1.png", Color.WHITE)
    val WEAPON_FIRE = Weapon("sword_fire", "نصل النار الملتهب", 60, 92f, 0.30f, "icons/item_2.png", Color.parseColor("#FF7043"))
    val WEAPON_CHRONO = Weapon("spear_chrono", "رمح كرونو الأسطوري", 95, 120f, 0.40f, "icons/item_5.png", Color.parseColor("#00E5FF"))
    val WEAPON_GOD = Weapon("godblade", "نصل الأبعاد الكوني", 160, 106f, 0.34f, "icons/blade.png", Color.parseColor("#E040FB"))

    val weapons = listOf(WEAPON_BASIC, WEAPON_FIRE, WEAPON_CHRONO, WEAPON_GOD)

    val ARMOR_ROYAL = Armor("armor_royal", "درع الفارس الملكي", 100, 0.30f)
    val ARMOR_NANO = Armor("armor_nano", "درع النانو المتطور", 150, 0.40f)
    val armors = listOf(ARMOR_ROYAL, ARMOR_NANO)

    fun weaponById(id: String): Weapon? = weapons.firstOrNull { it.id == id }
    fun armorById(id: String): Armor? = armors.firstOrNull { it.id == id }

    val shopItems = listOf(
        ShopItem("sword_fire", "نصل النار الملتهب", "سيف حارق. ضرر 60 ومدى أطول.", 60, ItemType.WEAPON, "icons/item_2.png"),
        ShopItem("spear_chrono", "رمح كرونو الأسطوري", "أطول مدى وأقوى ضربة: ضرر 95.", 140, ItemType.WEAPON, "icons/item_5.png"),
        ShopItem("armor_royal", "درع الفارس الملكي", "+100 صحة قصوى ويقلل الضرر 30%.", 80, ItemType.ARMOR, "ui/ico/shield.png", Color.parseColor("#FFD54F")),
        ShopItem("potion_heal", "جرعتا شفاء", "تستعيد 85 صحة. تُستخدم من زر العلاج.", 25, ItemType.CONSUMABLE, "icons/pot1.png"),
        ShopItem("bombs", "قنبلتان متفجرتان", "انفجار بمساحة يضرب كل من حوله.", 35, ItemType.CONSUMABLE, "icons/bomb.png"),
        ShopItem("up_hp", "قلب الحياة", "+30 صحة قصوى دائمة (حتى 3 مرات).", 90, ItemType.UPGRADE, "icons/pot33.png", null, 3),
        ShopItem("up_mana", "حجر الطاقة", "+25 طاقة سحر قصوى (حتى 3 مرات).", 70, ItemType.UPGRADE, "icons/pot10.png", null, 3)
    )
}
