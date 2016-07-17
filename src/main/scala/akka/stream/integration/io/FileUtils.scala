/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.stream.integration
package io

import java.io.{ File, FileNotFoundException }

import akka.stream.scaladsl.{ Flow, Source }
import akka.{ Done, NotUsed }
import org.apache.commons.io.{ FileUtils => CommonsFileUtils }

import scala.util.{ Failure, Success, Try }

sealed trait FileUtilsResult
final case class MoveFileResult(status: Try[Done]) extends FileUtilsResult
final case class CopyFileResult(status: Try[Done]) extends FileUtilsResult
final case class DeleteFileOrDirectoryResult(status: Try[Done]) extends FileUtilsResult
final case class FileExistsResult(status: Try[Done]) extends FileUtilsResult

sealed trait FileUtilsCommand
object FileUtilsCommand {
  case class MoveFile(srcFile: String, destFile: String) extends FileUtilsCommand
  case class CopyFile(srcFile: String, destFile: String) extends FileUtilsCommand
  case class DeleteFileOrDirectory(fileOrDir: String) extends FileUtilsCommand
  case class Exists(file: String) extends FileUtilsCommand
}

object FileUtils {

  /**
   * Moves a file.
   * <p>
   * When the destination file is on another file system, do a "copy and delete".
   */
  def moveFileTagged[T]: Flow[(T, FileUtilsCommand.MoveFile), (T, MoveFileResult), NotUsed] =
    Flow[(T, FileUtilsCommand.MoveFile)].flatMapConcat {
      case (tag, cmd) => Source.single(cmd).via(moveFile).map((tag, _))
    }

  /**
   * Moves a file.
   * <p>
   * When the destination file is on another file system, do a "copy and delete".
   */
  def moveFile: Flow[FileUtilsCommand.MoveFile, MoveFileResult, NotUsed] =
    Flow[FileUtilsCommand.MoveFile].map { cmd =>
      CommonsFileUtils.moveFile(new File(cmd.srcFile), new File(cmd.destFile))
      MoveFileResult(Success(Done))
    }.recover { case cause: Throwable => MoveFileResult(Failure(cause)) }

  /**
   * Copies a file to a new location preserving the file date.
   * <p>
   * This method copies the contents of the specified source file to the
   * specified destination file. The directory holding the destination file is
   * created if it does not exist. If the destination file exists, then this
   * method will overwrite it.
   */
  def copyFileTagged[T]: Flow[(T, FileUtilsCommand.CopyFile), (T, CopyFileResult), NotUsed] =
    Flow[(T, FileUtilsCommand.CopyFile)].flatMapConcat {
      case (tag, cmd) => Source.single(cmd).via(copyFile).map((tag, _))
    }

  /**
   * Copies a file to a new location preserving the file date.
   * <p>
   * This method copies the contents of the specified source file to the
   * specified destination file. The directory holding the destination file is
   * created if it does not exist. If the destination file exists, then this
   * method will overwrite it.
   */
  def copyFile: Flow[FileUtilsCommand.CopyFile, CopyFileResult, NotUsed] =
    Flow[FileUtilsCommand.CopyFile].map { cmd =>
      CommonsFileUtils.copyFile(new File(cmd.srcFile), new File(cmd.destFile))
      CopyFileResult(Success(Done))
    }.recover { case cause: Throwable => CopyFileResult(Failure(cause)) }

  /**
   * Deletes a file. If file is a directory, delete it and all sub-directories.
   * <ul>
   *   <li>A directory to be deleted does not have to be empty.</li>
   *   <li>Operation will fail when a file or directory cannot be deleted.</li>
   * </ul>
   */
  def deleteFileOrDirectoryTagged[T]: Flow[(T, FileUtilsCommand.DeleteFileOrDirectory), (T, DeleteFileOrDirectoryResult), NotUsed] =
    Flow[(T, FileUtilsCommand.DeleteFileOrDirectory)].flatMapConcat {
      case (tag, cmd) => Source.single(cmd).via(deleteFileOrDirectory).map((tag, _))
    }

  /**
   * Deletes a file. If file is a directory, delete it and all sub-directories.
   * <ul>
   *   <li>A directory to be deleted does not have to be empty.</li>
   *   <li>Operation will fail when a file or directory cannot be deleted.</li>
   * </ul>
   */
  def deleteFileOrDirectory: Flow[FileUtilsCommand.DeleteFileOrDirectory, DeleteFileOrDirectoryResult, NotUsed] =
    Flow[FileUtilsCommand.DeleteFileOrDirectory].map { cmd =>
      CommonsFileUtils.forceDelete(new File(cmd.fileOrDir))
      DeleteFileOrDirectoryResult(Success(Done))
    }.recover { case cause: Throwable => DeleteFileOrDirectoryResult(Failure(cause)) }

  /**
   * Checks whether or not a file exists
   */
  def existsTagged[T]: Flow[(T, FileUtilsCommand.Exists), (T, FileExistsResult), NotUsed] =
    Flow[(T, FileUtilsCommand.Exists)].flatMapConcat {
      case (tag, cmd) => Source.single(cmd).via(exists).map((tag, _))
    }

  /**
   * Checks whether or not a file exists
   */
  def exists: Flow[FileUtilsCommand.Exists, FileExistsResult, NotUsed] =
    Flow[FileUtilsCommand.Exists].map { cmd =>
      val file = new File(cmd.file)
      if (file.exists() && file.canRead && file.canWrite) FileExistsResult(Success(Done))
      else FileExistsResult(Failure(new FileNotFoundException(s"File '${cmd.file}' does not exists")))
    }.recover { case cause: Throwable => FileExistsResult(Failure(cause)) }
}
