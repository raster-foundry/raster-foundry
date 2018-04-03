package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.{Platform, User}

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._

import java.util.UUID

object PlatformDao extends Dao[Platform] {
    val tableName = "platforms"

    val selectF = sql"""
        SELECT
            id, created_at, created_by, modified_at, modified_by, name, settings
        FROM
    """ ++ tableF

    def create(platform: Platform): ConnectionIO[Platform] = {
        println(platform)
        (fr"INSERT INTO" ++ tableF ++ fr"""(
                id, created_at, created_by,
                modified_at, modified_by, name,
                settings
            )
            VALUES (
                ${platform.id}, ${platform.createdAt}, ${platform.createdBy},
                ${platform.modifiedAt}, ${platform.modifiedBy}, ${platform.name},
                ${platform.settings}
            )
        """).update.withUniqueGeneratedKeys[Platform](
            "id", "created_at", "created_by", "modified_at", "modified_by", "name", "settings"
        )
    }

    def update(platform: Platform, id: UUID, user: User): ConnectionIO[Int] = {
        (fr"UPDATE" ++ tableF ++ fr"""SET
            name = ${platform.name},
            modified_at = NOW(),
            modified_by = ${user.id},
            settings = ${platform.settings}
            where id = ${id}
        """).update.run
    }
}
