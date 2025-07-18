package com.llamatik.app.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.AirplaneTicket
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ConnectingAirports
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Surfing
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Now in Android icons. Material icons are [ImageVector]s, custom icons are drawable resource IDs.
 */
object LlamatikIcons {
    val ArrowBack = Icons.AutoMirrored.Filled.ArrowBack
    val ArrowDropDown = Icons.Rounded.ArrowDropDown
    val Bookmark = Icons.Rounded.Favorite
    val Check = Icons.Rounded.Check
    val Close = Icons.Rounded.Close
    val Person = Icons.Rounded.Person
    val Settings = Icons.Rounded.Settings
    val Info = Icons.Rounded.Info
    val Home = Icons.Rounded.Home
    val Inbox = Icons.Rounded.Email
    val Favorite = Icons.Rounded.Favorite
    val Exit = Icons.AutoMirrored.Rounded.ExitToApp
    val ArrowRight = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val Email = Icons.Rounded.Email
    val Password = Icons.Rounded.Password
    val Filter = Icons.Rounded.FilterList
    val Follow = Icons.Rounded.PersonAdd
    val Blog = Icons.AutoMirrored.Rounded.TextSnippet
    val Help = Icons.AutoMirrored.Rounded.Help
    val Pets = Icons.Rounded.Pets
    val BrokenImage = Icons.Rounded.BrokenImage
    val Shop = Icons.Rounded.ShoppingBasket
    val News = Icons.Rounded.RssFeed
    val Tools = Icons.Rounded.Construction
    val Server = Icons.Rounded.Dns
    val Devices = Icons.Rounded.Devices
    val FavoriteBorder = Icons.Rounded.FavoriteBorder
    val Share = Icons.Rounded.Share
    val Community = Icons.Rounded.People
    val Squadrons = Icons.Rounded.Groups
    val Following = Icons.Rounded.PersonAddAlt
    val Modules = Icons.Rounded.ConnectingAirports
    val Star = Icons.Rounded.Star
    val StarBorder = Icons.Rounded.StarBorder
    val Edit = Icons.Rounded.Edit
    val Flight = Icons.Rounded.Flight
    val Manual = Icons.AutoMirrored.Rounded.MenuBook
    val Pin = Icons.Rounded.PushPin
    val Volunteer = Icons.Rounded.VolunteerActivism
    val About = Icons.Rounded.Surfing
    val Play = Icons.Rounded.SportsEsports
    val Guide = Icons.Rounded.Book
    val Boarding = Icons.AutoMirrored.Rounded.AirplaneTicket
    val Download = Icons.Rounded.Download
    val Sort = Icons.AutoMirrored.Rounded.Sort
    val ChatBot = Icons.Rounded.Assistant
    val Microphone = Icons.Rounded.Mic
    val Send = Icons.Rounded.Send
    val Delete = Icons.Rounded.Delete
}

/**
 * A sealed class to make dealing with [ImageVector] and [DrawableRes] icons easier.
 */
sealed class Icon {
    data class ImageVectorIcon(val imageVector: ImageVector) : Icon()
    data class DrawableResourceIcon(val id: Int) : Icon()
}
