package com.earthbox.game

class Power(
    val id: String, val name: String, val icon: String,
    val hold: Boolean = false, val brush: Boolean = false
) {
    val isTool get() = id == "hand" || id == "inspect"
}

class Tab(val name: String, val icon: String, val powers: List<Power>)

val TABS: List<Tab> = listOf(
    Tab("Мир", "ui_map", listOf(
        Power("t_deep", "Глубина", "t_deep", true, true),
        Power("t_shal", "Мелко", "t_shallow", true, true),
        Power("t_sand", "Песок", "t_sand", true, true),
        Power("t_soil", "Земля", "t_dirt", true, true),
        Power("t_grass", "Трава", "t_grass", true, true),
        Power("t_stone", "Горы", "t_stone", true, true),
        Power("t_snow", "Снег", "t_snow", true, true),
        Power("t_lava", "Лава", "t_lava", true, true),
        Power("t_basalt", "Камень", "t_basalt", true, true),
        Power("tree", "Лес", "pine", true, true),
        Power("erase", "Ластик", "ui_x", true, true)
    )),
    Tab("Существа", "ui_heart", SP.indices.map { Power("sp$it", SP[it].name, SP[it].spr, true, true) }),
    Tab("Беды", "ic_fire", listOf(
        Power("fire", "Огонь", "ic_fire", true, true),
        Power("lightning", "Молния", "ic_lightning"),
        Power("meteor", "Метеор", "ic_meteor"),
        Power("bomb", "Бомба", "ic_meteor_hit"),
        Power("nuke", "Ядерка", "ic_nuke"),
        Power("acid", "Кислота", "ic_acid", true, true),
        Power("tornado", "Торнадо", "ic_tornado"),
        Power("hole", "Дыра", "fx_blackhole"),
        Power("quake", "Землетр.", "ic_ore"),
        Power("volcano", "Вулкан", "ic_volcano"),
        Power("flood", "Потоп", "big_wave")
    )),
    Tab("Природа", "ic_life", listOf(
        Power("life", "Жизнь", "ic_life", true, true),
        Power("fert", "Удобрить", "ic_spark", true, true),
        Power("rain", "Дождь", "fx_flash"),
        Power("snowfall", "Снегопад", "ui_snow"),
        Power("clear", "Ясно", "ui_star")
    )),
    Tab("Народы", "ui_crown", listOf(
        Power("found0", "Люди", "b_hut"),
        Power("found1", "Эльфы", "e_hut"),
        Power("found2", "Дворфы", "d_hut"),
        Power("found3", "Орки", "o_hut"),
        Power("war", "Война", "ui_flame"),
        Power("peace", "Мир", "ui_heart")
    )),
    Tab("Инструменты", "ui_gear", listOf(
        Power("hand", "Рука", "ui_orb"),
        Power("inspect", "Осмотр", "ui_q"),
        Power("erase", "Ластик", "ui_x", true, true)
    ))
)
