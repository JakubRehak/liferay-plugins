/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.sync.engine.documentlibrary.event;

import com.liferay.sync.engine.documentlibrary.handler.BaseHandler;
import com.liferay.sync.engine.model.SyncAccount;
import com.liferay.sync.engine.model.SyncFile;
import com.liferay.sync.engine.service.SyncAccountService;
import com.liferay.sync.engine.service.SyncFileService;
import com.liferay.sync.engine.util.FileUtil;
import com.liferay.sync.engine.util.IODeltaUtil;
import com.liferay.sync.engine.util.StreamUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Map;

/**
 * @author Shinn Lok
 */
public class DownloadFileEvent extends BaseEvent {

	public DownloadFileEvent(
		long syncAccountId, Map<String, Object> parameters) {

		super(syncAccountId, _URL_PATH, parameters);
	}

	@Override
	protected String processRequest() throws Exception {
		SyncFile syncFile = (SyncFile)getParameterValue("syncFile");

		syncFile.setState(SyncFile.STATE_IN_PROGRESS);
		syncFile.setUiEvent(SyncFile.UI_EVENT_DOWNLOADING);

		SyncFileService.update(syncFile);

		StringBuilder sb = new StringBuilder(9);

		sb.append(replaceURLPath(getSyncAccountId()));
		sb.append("/");
		sb.append(syncFile.getRepositoryId());
		sb.append("/");
		sb.append(syncFile.getTypeUuid());

		if ((Boolean)getParameterValue("patch")) {
			sb.append("?patch=true&sourceVersion=");
			sb.append(getParameterValue("sourceVersion"));
			sb.append("&targetVersion=");
			sb.append(getParameterValue("targetVersion"));
		}

		return executeGet(sb.toString(), new BaseHandler());
	}

	@Override
	protected void processResponse(String response) throws Exception {
		OutputStream outputStream = null;

		try {
			SyncFile syncFile = (SyncFile)getParameterValue("syncFile");

			Path filePath = Paths.get(syncFile.getFilePathName());

			if ((Boolean)getParameterValue("patch")) {
				InputStream inputStream = new ByteArrayInputStream(
					response.getBytes());

				IODeltaUtil.patch(filePath, inputStream);
			}
			else {
				outputStream = Files.newOutputStream(filePath);

				outputStream.write(response.getBytes());
			}

			syncFile.setFileKey(FileUtil.getFileKey(filePath));
			syncFile.setState(SyncFile.STATE_SYNCED);
			syncFile.setUiEvent(SyncFile.UI_EVENT_DOWNLOADED);

			SyncFileService.update(syncFile);
		}
		finally {
			StreamUtil.cleanUp(outputStream);
		}
	}

	protected String replaceURLPath(long syncAccountId) throws Exception {
		SyncAccount syncAccount = SyncAccountService.fetchSyncAccount(
			syncAccountId);

		String url = syncAccount.getUrl();

		URL urlObj = new URL(url);

		return url.replace(urlObj.getPath(), _URL_PATH);
	}

	private static final String _URL_PATH = "/sync-web/download";

}