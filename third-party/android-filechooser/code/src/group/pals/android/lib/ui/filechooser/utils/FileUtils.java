/*
 *    Copyright (c) 2012 Hai Bison
 *
 *    See the file LICENSE at the root directory of this project for copying
 *    permission.
 */

package group.pals.android.lib.ui.filechooser.utils;

import group.pals.android.lib.ui.filechooser.R;
import group.pals.android.lib.ui.filechooser.io.IFile;
import group.pals.android.lib.ui.filechooser.io.localfile.ParentFile;
import group.pals.android.lib.ui.filechooser.services.IFileProvider;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilities for files.
 * 
 * @author Hai Bison
 * @since v4.3 beta
 * 
 */
public class FileUtils {

    /**
     * Map of the regexes for file types corresponding to resource IDs for
     * icons.
     */
    private static final Map<String, Integer> _MapFileIcons = new HashMap<String, Integer>();

    static {
        _MapFileIcons.put(MimeTypes._RegexFileTypeAudios, R.drawable.afc_file_audio);
        _MapFileIcons.put(MimeTypes._RegexFileTypeVideos, R.drawable.afc_file_video);
        _MapFileIcons.put(MimeTypes._RegexFileTypeImages, R.drawable.afc_file_image);
        _MapFileIcons.put(MimeTypes._RegexFileTypeCompressed, R.drawable.afc_file_compressed);
        _MapFileIcons.put(MimeTypes._RegexFileTypePlainTexts, R.drawable.afc_file_plain_text);
    }

	private static boolean accessDenied(IFile file) {
		if (android.os.Build.VERSION.SDK_INT >= 9) {
    		return file instanceof File && !((File)file).canExecute();
		} else {
			return false;
		}
	}

    /**
     * Gets resource icon ID of an {@link IFile}.
     * 
     * @param file
     *            {@link IFile}
     * @return the resource icon ID
     */
    public static int getResIcon(IFile file, final IFileProvider.FilterMode filterMode) {
        if (file == null || !file.exists())
            return 0;//android.R.drawable.ic_delete;

        if (file.isFile()) {
            String filename = file.getName();
            for (String r : _MapFileIcons.keySet())
                if (filename.matches(r))
                    return _MapFileIcons.get(r);

            return R.drawable.afc_file;
        } else if (file.isDirectory()) {
            if (filterMode != IFileProvider.FilterMode.AnyDirectories) {
                if (file instanceof File && !((File)file).canWrite()) {
                    if (file instanceof ParentFile) {
                        return R.drawable.afc_folder;
                    } else if (accessDenied(file)) {
                        return R.drawable.afc_folder_no_access;
                    } else {
                        return R.drawable.afc_folder_locked;
                    }
                } else {
                    return R.drawable.afc_folder;
                }
            } else {
                if (accessDenied(file)) {
                    return R.drawable.afc_folder_no_access;
                } else {
                    return R.drawable.afc_folder;
                }
            }
        }

        return 0;//android.R.drawable.ic_delete;
    }// getResIcon()

    /**
     * Checks whether the filename given is valid or not.<br>
     * See <a href="http://en.wikipedia.org/wiki/Filename">wiki</a> for more
     * information.
     * 
     * @param name
     *            name of the file
     * @return {@code true} if the {@code name} is valid, and vice versa (if it
     *         contains invalid characters or it is {@code null}/ empty)
     */
    public static boolean isFilenameValid(String name) {
        return name != null && name.trim().matches("[^\\\\/?%*:|\"<>]+");
    }

    /**
     * Deletes a file or directory.
     * 
     * @param file
     *            {@link IFile}
     * @param fileProvider
     *            {@link IFileProvider}
     * @param recursive
     *            if {@code true} and {@code file} is a directory, browses the
     *            directory and deletes all of its sub files
     * @return the thread which is deleting files
     */
    public static Thread createDeleteFileThread(final IFile file, final IFileProvider fileProvider,
            final boolean recursive) {
        return new Thread() {

            @Override
            public void run() {
                deleteFile(file);
            }// run()

            private void deleteFile(IFile file) {
                if (isInterrupted())
                    return;

                if (file.isFile()) {
                    file.delete();
                    return;
                } else if (!file.isDirectory())
                    return;

                if (!recursive) {
                    file.delete();
                    return;
                }

                try {
                    List<IFile> files = fileProvider.listAllFiles(file);
                    if (files == null) {
                        file.delete();
                        return;
                    }

                    for (IFile f : files) {
                        if (isInterrupted())
                            return;

                        if (f.isFile())
                            f.delete();
                        else if (f.isDirectory()) {
                            if (recursive)
                                deleteFile(f);
                            else
                                f.delete();
                        }
                    }

                    file.delete();
                } catch (Throwable t) {
                    // TODO
                }
            }// deleteFile()
        };
    }// createDeleteFileThread()
}
