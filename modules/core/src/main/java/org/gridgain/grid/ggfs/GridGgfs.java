/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.ggfs;

import org.gridgain.grid.*;
import org.gridgain.grid.ggfs.mapreduce.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * <b>G</b>rid<b>G</b>ain <b>F</b>ile <b>S</b>ystem API. It provides a typical file system "view" on a particular cache:
 * <ul>
 *     <li>list directories or get information for a single path</li>
 *     <li>create/move/delete files or directories</li>
 *     <li>write/read data streams into/from files</li>
 * </ul>
 * The data of each file is split on separate data blocks and stored in the cache.
 * You can access file's data with a standard Java streaming API. Moreover, for each part
 * of the file you can calculate an affinity and process file's content on corresponding
 * nodes to escape unnecessary networking.
 * <p/>
 * This API is fully thread-safe and you can use it from several threads.
 * <h1 class="header">GGFS Configuration</h1>
 * The simplest way to run a GridGain node with configured file system is to pass
 * special configuration file included in GridGain distribution to {@code ggstart.sh} or
 * {@code ggstart.bat} scripts, like this: {@code ggstart.sh config/hadoop/default-config.xml}
 * <p>
 * {@code GGFS} can be started as a data node or as a client node. Data node is responsible for
 * caching data, while client node is responsible for basic file system operations and accessing
 * data nodes remotely. When used as Hadoop file system, clients nodes usually started together
 * with {@code job-submitter} or {@code job-scheduler} processes, while data nodes are usually
 * started together with Hadoop {@code task-tracker} processes.
 * <h1 class="header">Integration With Hadoop</h1>
 * In addition to direct file system API, {@code GGFS} can be integrated with {@code Hadoop} by
 * plugging in as {@code Hadoop FileSystem}. Refer to
 * {@code org.gridgain.grid.ggfs.hadoop.v1.GridGgfsHadoopFileSystem} or
 * {@code org.gridgain.grid.ggfs.hadoop.v2.GridGgfsHadoopFileSystem} for more information.
 * <p>
 * <b>NOTE:</b> integration with Hadoop is available only in {@code In-Memory Accelerator For Hadoop} edition.
 */
public interface GridGgfs {
    /** Property: user name. */
    public static final String PROP_USER_NAME = "usrName";

    /** Property: group name. */
    public static final String PROP_GROUP_NAME = "grpName";

    /** Property: permission. */
    public static final String PROP_PERMISSION = "permission";

    /**
     * Gets GGFS name.
     *
     * @return GGFS name.
     */
    @Nullable public String name();

    /**
     * Gets GGFS configuration.
     *
     * @return GGFS configuration.
     */
    public GridGgfsConfiguration configuration();

    /**
     * Checks if the specified path exists in the file system.
     *
     * @param path Path to check for existence in the file system.
     * @return {@code True} if such file exists, otherwise - {@code false}.
     * @throws GridException In case of error.
     */
    public boolean exists(GridGgfsPath path) throws GridException;

    /**
     * Gets file information for the specified path.
     *
     * @param path Path to get information for.
     * @return File information for specified path or {@code null} if such path does not exist.
     * @throws GridException In case of error.
     */
    @Nullable public GridGgfsFile info(GridGgfsPath path) throws GridException;

    /**
     * Gets summary (total number of files, total number of directories and total length)
     * for a given path.
     *
     * @param path Path to get information for.
     * @return Summary object.
     * @throws GridGgfsFileNotFoundException If path is not found.
     * @throws GridException If failed.
     */
    public GridGgfsPathSummary summary(GridGgfsPath path) throws GridException;

    /**
     * Updates file information for the specified path. Existent properties, not listed in the passed collection,
     * will not be affected. Other properties will be added or overwritten. Passed properties with {@code null} values
     * will be removed from the stored properties or ignored if they don't exist in the file info.
     * <p>
     * When working in {@code DUAL_SYNC} or {@code DUAL_ASYNC} modes only the following properties will be propagated
     * to the secondary file system:
     * <ul>
     * <li>{@code usrName} - file owner name;</li>
     * <li>{@code grpName} - file owner group;</li>
     * <li>{@code permission} - Unix-style string representing file permissions.</li>
     * </ul>
     *
     * @param path File path to set properties for.
     * @param props Properties to update.
     * @return File information for specified path or {@code null} if such path does not exist.
     * @throws GridException In case of error.
     */
    @Nullable public GridGgfsFile update(GridGgfsPath path, Map<String, String> props) throws GridException;

    /**
     * Renames/moves a file.
     * <p>
     * You are free to rename/move data files as you wish, but directories can be only renamed.
     * You cannot move the directory between different parent directories.
     * <p>
     * Examples:
     * <ul>
     *     <li>"/work/file.txt" => "/home/project/Presentation Scenario.txt"</li>
     *     <li>"/work" => "/work-2012.bkp"</li>
     *     <li>"/work" => "<strike>/backups/work</strike>" - such operation is restricted for directories.</li>
     * </ul>
     *
     * @param src Source file path to rename.
     * @param dest Destination file path. If destination path is a directory, then source file will be placed
     *     into destination directory with original name.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If source file doesn't exist.
     */
    public void rename(GridGgfsPath src, GridGgfsPath dest) throws GridException;

    /**
     * Deletes file.
     *
     * @param path File path to delete.
     * @param recursive Delete non-empty directories recursively.
     * @return {@code True} in case of success, {@code false} otherwise.
     * @throws GridException In case of error.
     */
    public boolean delete(GridGgfsPath path, boolean recursive) throws GridException;

    /**
     * Creates directories under specified path.
     *
     * @param path Path of directories chain to create.
     * @throws GridException In case of error.
     */
    public void mkdirs(GridGgfsPath path) throws GridException;

    /**
     * Creates directories under specified path with the specified properties.
     *
     * @param path Path of directories chain to create.
     * @param props Metadata properties to set on created directories.
     * @throws GridException In case of error.
     */
    public void mkdirs(GridGgfsPath path, @Nullable Map<String, String> props) throws GridException;

    /**
     * Lists file paths under the specified path.
     *
     * @param path Path to list files under.
     * @return List of files under the specified path.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If path doesn't exist.
     */
    public Collection<GridGgfsPath> listPaths(GridGgfsPath path) throws GridException;

    /**
     * Lists files under the specified path.
     *
     * @param path Path to list files under.
     * @return List of files under the specified path.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If path doesn't exist.
     */
    public Collection<GridGgfsFile> listFiles(GridGgfsPath path) throws GridException;

    /**
     * Opens a file for reading.
     *
     * @param path File path to read.
     * @return File input stream to read data from.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If path doesn't exist.
     */
    public GridGgfsInputStream open(GridGgfsPath path) throws GridException;

    /**
     * Opens a file for reading.
     *
     * @param path File path to read.
     * @param bufSize Read buffer size (bytes) or {@code zero} to use default value.
     * @return File input stream to read data from.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If path doesn't exist.
     */
    public GridGgfsInputStream open(GridGgfsPath path, int bufSize) throws GridException;

    /**
     * Opens a file for reading.
     *
     * @param path File path to read.
     * @param bufSize Read buffer size (bytes) or {@code zero} to use default value.
     * @param seqReadsBeforePrefetch Amount of sequential reads before prefetch is started.
     * @return File input stream to read data from.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If path doesn't exist.
     */
    public GridGgfsInputStream open(GridGgfsPath path, int bufSize, int seqReadsBeforePrefetch) throws GridException;

    /**
     * Creates a file and opens it for writing.
     *
     * @param path File path to create.
     * @param overwrite Overwrite file if it already exists. Note: you cannot overwrite an existent directory.
     * @return File output stream to write data to.
     * @throws GridException In case of error.
     */
    public GridGgfsOutputStream create(GridGgfsPath path, boolean overwrite) throws GridException;

    /**
     * Creates a file and opens it for writing.
     *
     * @param path File path to create.
     * @param bufSize Write buffer size (bytes) or {@code zero} to use default value.
     * @param overwrite Overwrite file if it already exists. Note: you cannot overwrite an existent directory.
     * @param affKey Affinity key used to store file blocks. If not {@code null}, the whole file will be
     *      stored on node where {@code affKey} resides.
     * @param replication Replication factor.
     * @param blockSize Block size.
     * @param props File properties to set.
     * @return File output stream to write data to.
     * @throws GridException In case of error.
     */
    public GridGgfsOutputStream create(GridGgfsPath path, int bufSize, boolean overwrite,
        @Nullable GridUuid affKey, int replication, long blockSize, @Nullable Map<String, String> props)
        throws GridException;

    /**
     * Opens an output stream to an existing file for appending data.
     *
     * @param path File path to append.
     * @param create Create file if it doesn't exist yet.
     * @return File output stream to append data to.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If path doesn't exist and create flag is {@code false}.
     */
    public GridGgfsOutputStream append(GridGgfsPath path, boolean create) throws GridException;

    /**
     * Opens an output stream to an existing file for appending data.
     *
     * @param path File path to append.
     * @param bufSize Write buffer size (bytes) or {@code zero} to use default value.
     * @param create Create file if it doesn't exist yet.
     * @param props File properties to set only in case it file was just created.
     * @return File output stream to append data to.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If path doesn't exist and create flag is {@code false}.
     */
    public GridGgfsOutputStream append(GridGgfsPath path, int bufSize, boolean create,
        @Nullable Map<String, String> props) throws GridException;

    /**
     * Sets last access time and last modification time for a given path. If argument is {@code null},
     * corresponding time will not be changed.
     *
     * @param path Path to update.
     * @param accessTime Optional last access time to set. Value {@code -1} does not update access time.
     * @param modificationTime Optional last modification time to set. Value {@code -1} does not update
     *      modification time.
     * @throws GridGgfsFileNotFoundException If target was not found.
     * @throws GridException If error occurred.
     */
    public void setTimes(GridGgfsPath path, long accessTime, long modificationTime) throws GridException;

    /**
     * Gets affinity block locations for data blocks of the file, i.e. the nodes, on which the blocks
     * are stored.
     *
     * @param path File path to get affinity for.
     * @param start Position in the file to start affinity resolution from.
     * @param len Size of data in the file to resolve affinity for.
     * @return Affinity block locations.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If path doesn't exist.
     */
    public Collection<GridGgfsBlockLocation> affinity(GridGgfsPath path, long start, long len) throws GridException;

    /**
     * Get affinity block locations for data blocks of the file. In case {@code maxLen} parameter is set and
     * particular block location length is greater than this value, block locations will be split into smaller
     * chunks.
     *
     * @param path File path to get affinity for.
     * @param start Position in the file to start affinity resolution from.
     * @param len Size of data in the file to resolve affinity for.
     * @param maxLen Maximum length of a single returned block location length.
     * @return Affinity block locations.
     * @throws GridException In case of error.
     * @throws GridGgfsFileNotFoundException If path doesn't exist.
     */
    public Collection<GridGgfsBlockLocation> affinity(GridGgfsPath path, long start, long len, long maxLen)
        throws GridException;

    /**
     * Gets metrics snapshot for this file system.
     *
     * @return Metrics.
     * @throws GridException In case of error.
     */
    public GridGgfsMetrics metrics() throws GridException;

    /**
     * Resets metrics for this file system.
     *
     * @throws GridException In case of error.
     */
    public void resetMetrics() throws GridException;

    /**
     * Determines size of the file denoted by provided path. In case if path is a directory, then
     * total size of all containing entries will be calculated recursively.
     *
     * @param path File system path.
     * @return Total size.
     * @throws GridException In case of error.
     */
    public long size(GridGgfsPath path) throws GridException;

    /**
     * Formats the file system removing all existing entries from it.
     *
     * @return Future completing when format is finished.
     * @throws GridException In case format has failed.
     */
    public GridFuture<?> format() throws GridException;

    /**
     * Executes GGFS task asynchronously.
     *
     * @param task Task to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param arg Optional task argument.
     * @return Execution future.
     * @throws GridException If execution failed.
     */
    public <T, R> GridFuture<R> execute(GridGgfsTask<T, R> task, @Nullable GridGgfsRecordResolver rslvr,
        Collection<GridGgfsPath> paths, @Nullable T arg) throws GridException;

    /**
     * Executes GGFS task asynchronously with overridden maximum range length (see
     * {@link GridGgfsConfiguration#getMaximumTaskRangeLength()} for more information).
     *
     * @param task Task to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param skipNonExistentFiles Whether to skip non existent files. If set to {@code true} non-existent files will
     *     be ignored. Otherwise an exception will be thrown.
     * @param maxRangeLen Optional maximum range length. If {@code 0}, then by default all consecutive
     *      GGFS blocks will be included.
     * @param arg Optional task argument.
     * @return Execution future.
     * @throws GridException If execution failed.
     */
    public <T, R> GridFuture<R> execute(GridGgfsTask<T, R> task, @Nullable GridGgfsRecordResolver rslvr,
        Collection<GridGgfsPath> paths, boolean skipNonExistentFiles, long maxRangeLen, @Nullable T arg)
        throws GridException;

    /**
     * Executes GGFS task asynchronously.
     *
     * @param taskCls Task class to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param arg Optional task argument.
     * @return Execution future.
     * @throws GridException If execution failed.
     */
    public <T, R> GridFuture<R> execute(Class<? extends GridGgfsTask<T, R>> taskCls,
        @Nullable GridGgfsRecordResolver rslvr, Collection<GridGgfsPath> paths, @Nullable T arg) throws GridException;

    /**
     * Executes GGFS task asynchronously with overridden maximum range length (see
     * {@link GridGgfsConfiguration#getMaximumTaskRangeLength()} for more information).
     *
     * @param taskCls Task class to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param skipNonExistentFiles Whether to skip non existent files. If set to {@code true} non-existent files will
     *     be ignored. Otherwise an exception will be thrown.
     * @param maxRangeLen Maximum range length.
     * @param arg Optional task argument.
     * @return Execution future.
     * @throws GridException If execution failed.
     */
    public <T, R> GridFuture<R> execute(Class<? extends GridGgfsTask<T, R>> taskCls,
        @Nullable GridGgfsRecordResolver rslvr, Collection<GridGgfsPath> paths, boolean skipNonExistentFiles,
        long maxRangeLen, @Nullable T arg) throws GridException;
}
