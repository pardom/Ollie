/*
 * Copyright (C) 2014 Michael Pardo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ollie.query;

import android.database.Cursor;
import ollie.Model;
import ollie.Ollie;
import rx.Observable;
import rx.Subscriber;

import java.util.List;

import static rx.Observable.OnSubscribe;

public abstract class ResultQueryBase extends ExecutableQueryBase implements ResultQuery {
	public ResultQueryBase(Query parent, Class<? extends Model> table) {
		super(parent, table);
	}

	@Override
	public <T extends Model> List<T> fetch() {
		return (List<T>) Ollie.rawQuery(mTable, getSql(), getArgs());
	}

	@Override
	public <T extends Model> T fetchSingle() {
		List<T> results = (List<T>) Ollie.rawQuery(mTable, getSql(), getArgs());
		if (!results.isEmpty()) {
			return results.get(0);
		}
		return null;
	}

	@Override
	public <T> T fetchValueAs(Class<T> type) {
		final Cursor cursor = Ollie.getDatabase().rawQuery(getSql(), getArgs());
		if (!cursor.moveToFirst()) {
			return null;
		}

		if (type.equals(Byte[].class) || type.equals(byte[].class)) {
			return (T) cursor.getBlob(0);
		} else if (type.equals(double.class) || type.equals(Double.class)) {
			return (T) Double.valueOf(cursor.getDouble(0));
		} else if (type.equals(float.class) || type.equals(Float.class)) {
			return (T) Float.valueOf(cursor.getFloat(0));
		} else if (type.equals(int.class) || type.equals(Integer.class)) {
			return (T) Integer.valueOf(cursor.getInt(0));
		} else if (type.equals(long.class) || type.equals(Long.class)) {
			return (T) Long.valueOf(cursor.getLong(0));
		} else if (type.equals(short.class) || type.equals(Short.class)) {
			return (T) Short.valueOf(cursor.getShort(0));
		} else if (type.equals(String.class)) {
			return (T) cursor.getString(0);
		}

		return null;
	}

	@Override
	public Observable<List<? extends Model>> observable() {
		return Observable.create(new OnSubscribe<List<? extends Model>>() {
			@Override
			public void call(Subscriber<? super List<? extends Model>> subscriber) {
				if (!subscriber.isUnsubscribed()) {
					subscriber.onNext(fetch());
					subscriber.onCompleted();
				}
			}
		});
	}

	@Override
	public Observable<? extends Model> observableSingle() {
		return Observable.create(new OnSubscribe<Model>() {
			@Override
			public void call(Subscriber<? super Model> subscriber) {
				if (!subscriber.isUnsubscribed()) {
					subscriber.onNext(fetchSingle());
					subscriber.onCompleted();
				}
			}
		});
	}

	@Override
	public <T> Observable<T> observableValueAs(final Class<T> type) {
		return Observable.create(new OnSubscribe<T>() {
			@Override
			public void call(Subscriber<? super T> subscriber) {
				if (!subscriber.isUnsubscribed()) {
					subscriber.onNext(fetchValueAs(type));
					subscriber.onCompleted();
				}
			}
		});
	}
}
