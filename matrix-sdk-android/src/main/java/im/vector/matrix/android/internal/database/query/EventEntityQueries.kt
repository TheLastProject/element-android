package im.vector.matrix.android.internal.database.query

import im.vector.matrix.android.internal.database.model.ChunkEntityFields
import im.vector.matrix.android.internal.database.model.EventEntity
import im.vector.matrix.android.internal.database.model.EventEntityFields
import im.vector.matrix.android.internal.database.model.RoomEntityFields
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.Sort
import io.realm.kotlin.where

internal fun EventEntity.Companion.where(realm: Realm, eventId: String): RealmQuery<EventEntity> {
    return realm.where<EventEntity>()
            .equalTo(EventEntityFields.EVENT_ID, eventId)
}

internal fun EventEntity.Companion.where(realm: Realm, roomId: String? = null, type: String? = null): RealmQuery<EventEntity> {
    val query = realm.where<EventEntity>()
    if (roomId != null) {
        query.equalTo("${EventEntityFields.CHUNK}.${ChunkEntityFields.ROOM}.${RoomEntityFields.ROOM_ID}", roomId)
    }
    if (type != null) {
        query.equalTo(EventEntityFields.TYPE, type)
    }
    return query
}

internal fun RealmQuery<EventEntity>.findMostSuitableStateEvent(stateIndex: Int): EventEntity? {
    val sort: Sort = if (stateIndex < 0) {
        this.greaterThanOrEqualTo(EventEntityFields.STATE_INDEX, stateIndex)
        Sort.ASCENDING
    } else {
        this.lessThanOrEqualTo(EventEntityFields.STATE_INDEX, stateIndex)
        Sort.DESCENDING
    }
    return this
            .sort(EventEntityFields.STATE_INDEX, sort)
            .findFirst()
}


internal fun RealmQuery<EventEntity>.last(): EventEntity? {
    return this
            .sort(EventEntityFields.STATE_INDEX, Sort.DESCENDING)
            .findFirst()
}

internal fun RealmList<EventEntity>.find(eventId: String): EventEntity? {
    return this.where().equalTo(EventEntityFields.EVENT_ID, eventId).findFirst()
}

internal fun RealmList<EventEntity>.fastContains(eventId: String): Boolean {
    return this.find(eventId) != null
}
