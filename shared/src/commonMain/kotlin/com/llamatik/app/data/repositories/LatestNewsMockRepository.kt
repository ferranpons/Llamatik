package com.llamatik.app.data.repositories

import com.llamatik.app.common.model.NewsModel

class LatestNewsMockRepository {
    private val itemList: List<NewsModel> = listOf(
        NewsModel(
            0,
            "DCS update is out now!",
            "",
            "At last we made it. Once again, please accept our apologies for the delayed release of the 2.9.6 version of DCS which includes our new Launcher, DCS: Afghanistan map, DCS: Supercarrier features, Flaming Cliffs 2024, several new campaigns, and much more. Please update now and enjoy! Check out the Flaming Cliffs 2024 video here and for those of you who own Flaming Cliffs 3, you can upgrade to FC2024 for only \$9,99 for the coming month, thereafter the upgrade price will increase to \$14,99. Thank you. ",
            "https://www.digitalcombatsimulator.com/images/newsletter/20200410/ed_logo-250x150.png",
            "2024/07/14",
            ""
        ),
        NewsModel(
            0,
            "DCS version 2.9.6 is delayed",
            "",
            "The next DCS update, version 2.9.6, has been delayed and is currently in heavy testing and bug fixing. We sincerely apologise for any inconvenience this may have caused. It is very important for us to release updates with as few issues as possible and this extra time is genuinely required to deliver a quality product.",
            "https://www.digitalcombatsimulator.com/images/newsletter/20200410/ed_logo-250x150.png",
            "2024/07/05",
            ""
        ),
        NewsModel(
            0,
            "Summer Sale",
            "",
            "The DCS Summer Sale 2024 is your chance to save big across nearly our entire range of aircraft, terrains campaigns and important add-ons. Hurry! The sale lasts until the 14th of July, 2024 at 15:00 GMT on our E-Shop where you will find huge savings across all our most popular products. Also, the DCS World Steam Edition sale is now open until the 11th of July, 2024 at 17:00 GMT.",
            "https://www.digitalcombatsimulator.com/images/newsletter/20200410/ed_logo-250x150.png",
            "2024/06/28",
            ""
        ),
        NewsModel(
            0,
            "Summer Savings",
            "",
            "The biggest savings of the year are now available with our DCS Summer Sale that will be running until the 14th of July 2024 at 15:00 GMT. Go and grab 50% discounts on a wide range of modules and terrains. Enjoy!",
            "https://www.digitalcombatsimulator.com/images/newsletter/20200410/ed_logo-250x150.png",
            "2024/06/21",
            ""
        ),
        NewsModel(
            0,
            "Flaming Cliffs 2024 is dropping in the next update",
            "",
            "We are thrilled to announce the release of Flaming Cliffs 2024 in the next update! This new release will enhance your DCS experience with new Flaming Cliffs level aircraft such as the F-5E, F-86F and MiG-15bis. ",
            "https://www.digitalcombatsimulator.com/upload/iblock/35f/dwb0kdxj9nk25duxvtdt2qqm8p0vkgma/FC4_Eshop_cover_700x1000.png",
            "2024/06/14",
            ""
        ),
        NewsModel(
            0,
            "Launch of DCS: OH-58D Kiowa Warrior!",
            "",
            "We are delighted to announce the highly anticipated release of the DCS: OH-58D Kiowa Warrior by Polychop Simulations for DCS. This iconic helicopter guarantees an exceptional and highly accurate experience for both veteran and novice pilots. \n" +
                    "\n" +
                    "The OH-58D Kiowa Warrior is a versatile armed reconnaissance helicopter that has served in various military conflicts around the globe. Known for its agility, advanced surveillance capabilities, and firepower, the OH-58D has been a vital asset in recon and light attack missions. Watch the trailer!",
            "https://www.digitalcombatsimulator.com/upload/iblock/54b/7cor1sz6q5jnqgl2yd3fv9kurbin327z/dcs-world-oh-58d-kiowa-warrior.jpg",
            "2024/07/12",
            ""
        )
    )

    fun getAds(): Result<List<NewsModel>> = runCatching {
        return@runCatching itemList
    }

    fun getFeaturedAd(id: Int): NewsModel? {
        return itemList.find { id == it.id }
    }
}
