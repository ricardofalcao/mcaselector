package net.querz.mcaselector.ui.dialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.querz.mcaselector.Config;
import net.querz.mcaselector.debug.Debug;
import net.querz.mcaselector.io.ByteArrayPointer;
import net.querz.mcaselector.io.CompressionType;
import net.querz.mcaselector.io.FileHelper;
import net.querz.mcaselector.io.MCAChunkData;
import net.querz.mcaselector.io.MCAFile;
import net.querz.mcaselector.point.Point2i;
import net.querz.mcaselector.property.DataProperty;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.tiles.Tile;
import net.querz.mcaselector.tiles.TileMap;
import net.querz.mcaselector.ui.NBTTreeView;
import net.querz.mcaselector.ui.UIFactory;
import net.querz.nbt.tag.ByteArrayTag;
import net.querz.nbt.tag.ByteTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.DoubleTag;
import net.querz.nbt.tag.EndTag;
import net.querz.nbt.tag.FloatTag;
import net.querz.nbt.tag.IntArrayTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongArrayTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.ShortTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;

public class NBTEditorDialog extends Dialog<NBTEditorDialog.Result> {

	private final Map<Integer, Label> addTagLabels = new LinkedHashMap<>();
	private CompoundTag data;
	private Point2i regionLocation;
	private Point2i chunkLocation;

	private final BorderPane treeViewHolder = new BorderPane();
	private final Label treeViewPlaceHolder = UIFactory.label(Translation.DIALOG_EDIT_NBT_PLACEHOLDER_LOADING);

	public NBTEditorDialog(TileMap tileMap, Stage primaryStage) {
		titleProperty().bind(Translation.DIALOG_EDIT_NBT_TITLE.getProperty());
		initStyle(StageStyle.UTILITY);
		getDialogPane().getStyleClass().add("nbt-editor-dialog-pane");
		setResultConverter(p -> p == ButtonType.APPLY ? new Result(data) : null);
		getDialogPane().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
		getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
		getDialogPane().lookupButton(ButtonType.APPLY).setDisable(true);
		((Button) getDialogPane().lookupButton(ButtonType.APPLY)).setOnAction(e -> writeSingleChunk());

		NBTTreeView nbtTreeView = new NBTTreeView(primaryStage);

		ImageView deleteIcon = new ImageView(FileHelper.getIconFromResources("img/delete"));
		Label delete = new Label("", deleteIcon);
		delete.getStyleClass().add("nbt-editor-delete-tag-label");
		delete.setDisable(true);
		deleteIcon.setPreserveRatio(true);
		delete.setOnMouseEntered(e -> {
			if (!delete.isDisabled()) {
				deleteIcon.setFitWidth(24);
			}
		});
		delete.setOnMouseExited(e -> {
			if (!delete.isDisabled()) {
				deleteIcon.setFitWidth(22);
			}
		});
		delete.disableProperty().addListener((i, o, n) -> {
			if (o.booleanValue() != n.booleanValue()) {
				if (n) {
					delete.getStyleClass().remove("nbt-editor-delete-tag-label-enabled");
				} else {
					delete.getStyleClass().add("nbt-editor-delete-tag-label-enabled");
				}
			}
		});

		delete.setOnMouseClicked(e -> nbtTreeView.deleteItem(nbtTreeView.getSelectionModel().getSelectedItem()));
		nbtTreeView.setOnSelectionChanged((o, n) -> {
			delete.setDisable(n == null || n.getParent() == null);
			enableAddTagLabels(nbtTreeView.getPossibleChildTagTypes(n));
		});

		HBox options = new HBox();
		options.getStyleClass().add("nbt-editor-options");

		treeViewHolder.getStyleClass().add("nbt-tree-view-holder");

		initAddTagLabels(nbtTreeView);
		options.getChildren().add(delete);
		options.getChildren().addAll(addTagLabels.values());

		VBox box = new VBox();

		treeViewHolder.setCenter(treeViewPlaceHolder);

		box.getChildren().addAll(treeViewHolder, options);

		getDialogPane().setContent(box);

		readSingleChunkAsync(tileMap, nbtTreeView);
	}

	private void enableAddTagLabels(int[] ids) {
		for (Map.Entry<Integer, Label> label : addTagLabels.entrySet()) {
			label.getValue().setDisable(true);
		}
		if (ids != null) {
			for (int id : ids) {
				addTagLabels.get(id).setDisable(false);
			}
		}
	}

	private void initAddTagLabels(NBTTreeView nbtTreeView) {
		addTagLabels.put(1, iconLabel("img/nbt/byte", ByteTag::new, nbtTreeView));
		addTagLabels.put(2, iconLabel("img/nbt/short", ShortTag::new, nbtTreeView));
		addTagLabels.put(3, iconLabel("img/nbt/int", IntTag::new, nbtTreeView));
		addTagLabels.put(4, iconLabel("img/nbt/long", LongTag::new, nbtTreeView));
		addTagLabels.put(5, iconLabel("img/nbt/float", FloatTag::new, nbtTreeView));
		addTagLabels.put(6, iconLabel("img/nbt/double", DoubleTag::new, nbtTreeView));
		addTagLabels.put(8, iconLabel("img/nbt/string", StringTag::new, nbtTreeView));
		addTagLabels.put(9, iconLabel("img/nbt/list", () -> ListTag.createUnchecked(EndTag.class), nbtTreeView));
		addTagLabels.put(10, iconLabel("img/nbt/compound", CompoundTag::new, nbtTreeView));
		addTagLabels.put(7, iconLabel("img/nbt/byte_array", ByteArrayTag::new, nbtTreeView));
		addTagLabels.put(11, iconLabel("img/nbt/int_array", IntArrayTag::new, nbtTreeView));
		addTagLabels.put(12, iconLabel("img/nbt/long_array", LongArrayTag::new, nbtTreeView));
		// disable all add tag labels
		enableAddTagLabels(null);
	}

	private Label iconLabel(String img, Supplier<Tag<?>> tagSupplier, NBTTreeView nbtTreeView) {
		ImageView icon = new ImageView(FileHelper.getIconFromResources(img));
		Label label = new Label("", icon);
		icon.setPreserveRatio(true);
		label.setOnMouseEntered(e -> icon.setFitWidth(18));
		label.setOnMouseExited(e -> icon.setFitWidth(16));
		label.getStyleClass().add("nbt-editor-add-tag-label");
		label.setOnMouseClicked(e -> nbtTreeView.addItem(nbtTreeView.getSelectionModel().getSelectedItem(), "Unknown", tagSupplier.get()));
		return label;
	}

	private void readSingleChunkAsync(TileMap tileMap, NBTTreeView treeView) {
		new Thread(() -> {
			Map<Point2i, Set<Point2i>> selection = tileMap.getMarkedChunks();
			DataProperty<Point2i> region = new DataProperty<>();
			DataProperty<Point2i> chunk = new DataProperty<>();
			selection.forEach((k, v) -> {
				region.set(k);
				v.forEach(chunk::set);
			});
			regionLocation = region.get();
			chunkLocation = chunk.get();
			File file = FileHelper.createMCAFilePath(region.get());
			Debug.dumpf("attempting to read single chunk from file: %s", chunk.get());
			if (file.exists()) {
				MCAChunkData chunkData = MCAFile.readSingleChunk(file, chunk.get());
				if (chunkData == null || chunkData.getData() == null) {
					Debug.dump("no chunk data found for:" + chunk.get());
					Platform.runLater(() -> treeViewHolder.setCenter(UIFactory.label(Translation.DIALOG_EDIT_NBT_PLACEHOLDER_NO_CHUNK_DATA)));
					return;
				}
				data = chunkData.getData();
				Platform.runLater(() -> {
					treeView.setRoot(chunkData.getData());
					treeViewHolder.setCenter(treeView);
					getDialogPane().lookupButton(ButtonType.APPLY).setDisable(false);
				});
			} else {
				Platform.runLater(() -> treeViewHolder.setCenter(UIFactory.label(Translation.DIALOG_EDIT_NBT_PLACEHOLDER_NO_REGION_FILE)));
			}
		}).start();
	}

	private void writeSingleChunk() {
		File file = FileHelper.createMCAFilePath(regionLocation);

		byte[] data = new byte[(int) file.length()];
		try (FileInputStream fis = new FileInputStream(file)) {
			fis.read(data);
		} catch (IOException ex) {
			Debug.dumpException(String.format("failed to read MCAFile %s to write single chunk", file), ex);
			return;
		}

		MCAFile dest = MCAFile.readAll(file, new ByteArrayPointer(data));

		Point2i rel = chunkLocation.mod(32);
		rel.setX(rel.getX() < 0 ? 32 + rel.getX() : rel.getX());
		rel.setZ(rel.getZ() < 0 ? 32 + rel.getZ() : rel.getZ());
		int index = rel.getZ() * Tile.SIZE_IN_CHUNKS + rel.getX();

		MCAChunkData chunkData = dest.getChunkData(index);
		chunkData.setData(this.data);
		chunkData.setCompressionType(Config.getDefaultChunkCompressionType());
		dest.setChunkData(index, chunkData);
		dest.setTimeStamp(index, (int) (System.currentTimeMillis() / 1000));
		try {
			File tmpFile = File.createTempFile(file.getName(), null, null);
			try (RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw")) {
				dest.saveAll(raf);
			}
			Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ex) {
			Debug.dumpException(String.format("failed to write single chunk to MCAFile %s after editing", file), ex);
		}
	}

	public static class Result {

		private final CompoundTag data;

		private Result(CompoundTag data) {
			this.data = data;
		}

		public CompoundTag getData() {
			return data;
		}
	}
}
