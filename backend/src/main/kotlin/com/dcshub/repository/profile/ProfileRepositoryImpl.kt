package com.dcshub.repository.profile

import com.dcshub.app.common.model.GeoLocation
import com.dcshub.app.common.model.PlayerStats
import com.dcshub.app.common.model.Profile
import com.dcshub.repository.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.update

class ProfileRepositoryImpl : ProfileRepository {

    override suspend fun addProfile(
        id: Int,
        name: String,
        nickname: String,
        description: String?,
        image: String?,
        location: GeoLocation?,
        preferredLanguage: String?,
        serversList: List<String>?,
        rank: Int?,
        country: String?,
        squadron: String?,
        squadronPatch: String?,
        playerStats: PlayerStats,
        medals: List<String>?
    ): Profile? {
        var statement: InsertStatement<Number>? = null
        dbQuery {
            statement = Profiles.insert { profiles ->
                profiles[Profiles.userId] = userId
                profiles[Profiles.name] = name
                description?.let {
                    profiles[Profiles.description] = it
                }
                image?.let {
                    profiles[Profiles.image] = it
                }
            }
        }
        return rowToProfiles(statement?.resultedValues?.get(0))
    }

    override suspend fun getProfile(userId: Int): Profile? {
        return dbQuery {
            Profiles.select(Profiles.userId).where {
                Profiles.userId.eq((userId))
            }.mapNotNull { rowToProfiles(it) }
        }.firstOrNull()
    }

    override suspend fun updateProfile(
        userId: Int,
        name: String?,
        nickname: String,
        description: String?,
        image: String?,
        location: String?,
        preferredLanguage: String?,
        serversList: List<String>?,
        rank: Int?,
        country: String?,
        squadron: String?,
        squadronPatch: String?,
        playerStats: PlayerStats,
        medals: List<String>?
    ): Profile? {
        return dbQuery {
            Profiles.select(Profiles.userId).where {
                Profiles.userId.eq((userId))
            }.forUpdate()

            Profiles.update {
                Profiles.userId.eq(userId)
                name?.let { name ->
                    it[Profiles.name] = name
                }
                description?.let { description ->
                    it[Profiles.description] = description
                }
                image?.let { image ->
                    it[Profiles.image] = image
                }
                location?.let { location ->
                    it[Profiles.location] = location
                }
            }

            Profiles.select(Profiles.userId).where {
                Profiles.userId.eq((userId))
            }.mapNotNull { rowToProfiles(it) }
        }.firstOrNull()
    }

    private fun rowToProfiles(row: ResultRow?): Profile? {
        if (row == null) {
            return null
        }
        val geoLocation = getGeoLocationObjectFrom(row[Profiles.location])
        return Profile(
            id = row[Profiles.id],
            userId = row[Profiles.userId],
            name = row[Profiles.name],
            description = row[Profiles.description],
            image = row[Profiles.image],
            location = geoLocation
        )
    }

    private fun getGeoLocationObjectFrom(rowText: String): GeoLocation {
        val geoLocationText = rowText.split(',')
        return if (geoLocationText.size > 1) {
            GeoLocation(geoLocationText[0].toDouble(), geoLocationText[1].toDouble())
        } else {
            GeoLocation(0.0, 0.0)
        }
    }
}
