package controllers.admin;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import main.ApplicationController;
import entities.Node;
import entities.Professional;
import entities.Room;
import controllers.filereader.FileParser;
import controllers.shared.MapDisplayController;
import main.algorithms.Pathfinder;
import main.algorithms.Algorithm;
import main.database.DatabaseWrapper;
import entities.FloorImage;
import entities.FloorProxy;

public class EditorController extends MapDisplayController
		implements Initializable
{
	private static final boolean DEBUGGING = false;

	@FXML
	private Button addRoomBtn;
	//@FXML
	//private Button addUserButton;
	@FXML
	private Button logoutBtn;
	@FXML
	private TextField nameField;
	@FXML
	private TextArea descriptField;
	@FXML
	private TextField xCoordField;
	@FXML
	private TextField yCoordField;
	@FXML
	private ImageView imageViewMap;
	@FXML
	private Button modifyRoomBtn;
	@FXML
	private Button cancelBtn;
	@FXML
	private Button deleteRoomBtn;
	@FXML
	private Button confirmBtn;
//	@FXML
//	private ChoiceBox<Professional> proChoiceBox;
	@FXML
	private Button addCustomProBtn;
	@FXML
	private Button deleteProfBtn;
	@FXML
	protected Pane linePane;
	@FXML
	protected Pane nodePane;
	@FXML
	public AnchorPane contentAnchor = new AnchorPane();
	@FXML
	public ComboBox<FloorProxy> floorComboBox;
	@FXML
	public ComboBox buildingComboBox;
	@FXML
	public TableView<Professional> roomProfTable;
	@FXML
	private TableColumn<Professional, String> roomCol;
	@FXML
	private TableColumn<Professional, String> profCol;
	@FXML
	private Text roomName;
	@FXML
	private Text yPos;
	@FXML
	private Label xPos;
	@FXML
	private ComboBox<Algorithm> algorithmComboBox;
	@FXML
	private BorderPane parentBorderPane;
	@FXML
	private ScrollPane mapScroll = new ScrollPane();
	@FXML
	private Button helpBtn;


//	protected Node selectedNode; // you select a node by double clicking
	protected ArrayList<Node> selectedNodes = new ArrayList<>();
	protected boolean shiftPressed = false;
	protected double selectionStartX;
	protected double selectionStartY;
	protected double selectionEndX;
	protected double selectionEndY;
	protected boolean draggingNode = false; // This is so that the selection box does not show up when dragging a node or group of nodes
	protected boolean draggedANode = false; // This is to prevent deselection of a node after dragging it
	protected boolean ctrlClicked = false;

	protected boolean toggleShowRooms = false; // this is to enable/disable label editing


	final double SCALE_DELTA = 1.1;
	protected static double SCALE_TOTAL = 1;
	final protected double zoomMin = 1;
	final protected double zoomMax = 6;
	private double clickedX, clickedY; //Where we clicked on the anchorPane
	private boolean beingDragged; //Protects the imageView for being dragged

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		//Load
		this.setPanes(linePane, nodePane); //Set the panes
		directory = ApplicationController.getDirectory(); //Grab the database controller from main and use it to populate our directory
		iconController = ApplicationController.getIconController();

		this.changeFloor(getFloor());

		this.imageViewMap.setPickOnBounds(true);
		if(floorComboBox != null) {
			initfloorComboBox();
		}

		// TODO: Move zoom initialization to separate function and call in installPaneListeners
		// I tested this value, and we want it to be defaulted here because the map does not start zoomed out all the way
		zoomSlider.setValue(0);
		zoomSlider.valueProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable,
			                    Number oldValue, Number newValue) {
				/**
				 * This one was a fun one.
				 * This math pretty much makes it so when the slider is at the far left, the map will be zoomed out all the way
				 * and when it's at the far right, it will be zoomed in all the way
				 * when it's at the left, zoomPercent is 0, so we want the full value of zoomMin to be the zoom coefficient
				 * when it's at the right, zoomPercent is 1, and we want the full value of zoomMax to be the zoom coefficient
				 * the equation is just that
				 */
				double zoomPercent = (zoomSlider.getValue()/100);
				double zoomCoefficient = zoomMin*(1 - zoomPercent) + zoomMax*(zoomPercent);
				mapScroll.setScaleX(zoomCoefficient);
				mapScroll.setScaleY(zoomCoefficient);
			}
		});

		this.redisplayGraph(); // redraw nodes and edges
		this.iconController.resetAllNodes();

		//Lets us click through items
		this.imageViewMap.setPickOnBounds(true);
		this.contentAnchor.setPickOnBounds(false);
		this.topPane.setPickOnBounds(false);

		this.installPaneListeners();
		this.setUpAlgorithmChoiceBox();

		// Add listeners to all nodes
		this.directory.getNodes().forEach(this::addNodeListeners);

		//Populate the tableview
		HashSet<Room> locations = new HashSet<>();
		for (Professional p: directory.getProfessionals()) {
			locations.addAll(p.getLocations());

		}
		this.populateTableView();

		//Listener for the tableview
		roomProfTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if (roomProfTable.getSelectionModel().getSelectedItem() != null) {
				// TODO: Allow professional selection from the TableView
				//selectedLocation = newValue;
			}
		});


		/** This is the section for key listeners.
		 *  Press Back Space for Deleting selected nodes
		 *  Press Ctrl + A for selecting all nodes
		 *  Press Ctrl + Open Bracket for zoom in
		 *  Press Ctrl + Close Bracket for zoom out
		 *  Press Shift + Right to move the view to the right
		 *  Press Shift + Left to move the view to the left
		 *  Press Shift + Up to move the view to the up
		 *  Press Shift + down to move the view to the down
		 */
		// TODO: Use control+plus/minus for zooming
		parentBorderPane.setOnKeyPressed(e -> {
//			System.out.println(e); // Prints out key statements
			System.out.println(e.getCode());// Prints out key statements
			if (e.getCode() == KeyCode.OPEN_BRACKET && e.isControlDown()) {
				increaseZoomButtonPressed();
			}else if (e.getCode() == KeyCode.CLOSE_BRACKET && e.isControlDown()) {
				decreaseZoomButtonPressed();
			}else if (e.getCode() == KeyCode.RIGHT && e.isShiftDown()) {
				contentAnchor.setTranslateX(contentAnchor.getTranslateX() - 10);
			}else if (e.getCode() == KeyCode.LEFT && e.isShiftDown()) {
				contentAnchor.setTranslateX(contentAnchor.getTranslateX() + 10);
			}else if (e.getCode() == KeyCode.UP && e.isShiftDown()) {
				contentAnchor.setTranslateY(contentAnchor.getTranslateY() + 10);
			}else if (e.getCode() == KeyCode.DOWN && e.isShiftDown()) {
				contentAnchor.setTranslateY(contentAnchor.getTranslateY() - 10);
			}
			e.consume();
		});
	}


	// TODO: rename descriptively
	public void populateTableView() {
		Collection<Professional> profs = directory.getProfessionals();

//		roomCol.setCellValueFactory(cdf -> new SimpleStringProperty(cdf.getValue().toString()));

		roomCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Professional, String>, ObservableValue<String>>() {
			@Override
			public ObservableValue<String> call(TableColumn.CellDataFeatures<Professional, String> cdf) {
				StringJoiner roomList = new StringJoiner(", ");
				for (Room r : cdf.getValue().getLocations()) {
					roomList.add(r.getName());
				}
				return new SimpleStringProperty(roomList.toString());
			}
		});

		profCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Professional, String>, ObservableValue<String>>() {
			@Override
			public ObservableValue<String> call(TableColumn.CellDataFeatures<Professional, String> cdf) {
				return new SimpleStringProperty(cdf.getValue().getSurname()+", "+cdf.getValue().getGivenName()+" "+cdf.getValue().getTitle());
			}
		});

//		profCol.setCellValueFactory(new PropertyValueFactory<>("givenName"));

		roomProfTable.getSortOrder().add(profCol);
		roomProfTable.getSortOrder().add(roomCol);

		roomProfTable.getItems().setAll(profs);

		this.roomProfTable.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Professional>() {
			@Override
			public void changed(ObservableValue<? extends Professional> observable, Professional oldValue, Professional newValue) {
				selectedProf = newValue;
			}
		});


	}

	@FXML
	private void logoutBtnClicked() {
		if (! directory.roomsAreConnected()) {
			Alert warn = new Alert(Alert.AlertType.CONFIRMATION, "Not all rooms are connected: some paths will not exist.");
			// true if and only if the button pressed in the alert did not say "OK"
			if (! warn.showAndWait().map(result -> "OK".equals(result.getText())).orElse(false)) {
				return;
			}
		}

		try {
			Parent userUI = (BorderPane) FXMLLoader.load(this.getClass().getResource("/UserDestination.fxml"));
			this.botPane.getScene().setRoot(userUI);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void changeFloor(FloorImage floor) {
		Image map = this.switchFloors(floor);
		this.imageViewMap.setImage(map);
		this.redisplayGraph();
	}

	@FXML
	public void addProfToRoom() {
		if (this.selectedProf == null || this.selectedNodes.size() == 0) return;

		this.selectedNodes.forEach(n-> n.applyToRoom(room -> directory.addRoomToProfessional(room, this.selectedProf)));

		this.populateTableView();
	}

	@FXML
	public void delProfFromRoom() {
		if (this.selectedNodes.size() == 0 || this.selectedProf == null) return;

		this.selectedNodes.forEach(n-> n.applyToRoom(room -> directory.removeRoomFromProfessional(room, this.selectedProf)));

		this.populateTableView();
	}

	@FXML
	public void addCustomProBtnPressed() throws IOException {
		AddProfessionalController addProController = new AddProfessionalController();
		FXMLLoader loader = new FXMLLoader();
		loader.setLocation(this.getClass().getResource("/AddProUI.fxml"));
		Scene addProScene = new Scene(loader.load());
		Stage addProStage = new Stage();
		addProStage.initOwner(contentAnchor.getScene().getWindow());
		addProStage.setScene(addProScene);
		addProStage.showAndWait();
		this.populateTableView();
	}

	@FXML
	public void deleteProfBtnClicked () {
		this.directory.removeProfessional(this.selectedProf);
		this.populateTableView();
	}


	@FXML
	public void confirmBtnPressed() {
//		this.directory.getRooms().forEach(room ->
//				System.out.println("Attempting to save room: "+room.getName()+" to database..."));
		DatabaseWrapper.saveDirectory(this.directory);
	}

	@FXML
	public void addRoomBtnClicked() {
		if(this.yCoordField.getText().isEmpty() || this.xCoordField.getText().isEmpty()){
			if(this.yCoordField.getText().isEmpty()){
				yPos.setFill(Color.RED);
			}
			if(this.xCoordField.getText().isEmpty()){
				xPos.setTextFill(Color.RED);
			}
			return;
		}

		this.addNodeRoom(this.readX(), this.readY(), this.nameField.getText(), this.descriptField.getText());
	}

	@FXML
	public void modifyRoomBtnClicked() {
		if(this.selectedNodes.size() != 1) return;

		this.updateSelectedRoom(this.readX(), this.readY(), this.nameField.getText(), this.descriptField.getText());
	}

	@FXML
	public void deleteRoomBtnClicked() {
		this.deleteSelectedNodes();
	}


	/* **** Non-FXML functions **** */

	/**
	 * Redraw all elements of the map and the professionals' elements
	 */
	private void redisplayAll() {
		this.redisplayGraph(); // nodes on this floor and lines between them
		this.populateTableView();
	}

	/**
	 * Redisplay the nodes on this floor and the lines
	 *
	 * If debugging, display all nodes
	 */
	private void redisplayGraph() {
//		if (EditorController.DEBUGGING) {
//			this.displayNodes(directory.getNodes());
//			this.redrawLines(directory.getNodes());
//		} else {
		this.displayNodes(directory.getNodesOnFloor(getFloor()));
		this.redrawLines();
//		}
	}

	/**
	 * This function is currently for testing purposes only
	 * Using it will modifier listeners that the user can access; be careful
	 */
	public void displayRoomsOnFloor() {
		Set<javafx.scene.Node> roomShapes = new HashSet<>();
		for (Room r : directory.getRoomsOnFloor(floor)) {
			roomShapes.add(r.getUserSideShape());
			r.getUserSideShape().setOnMouseClicked(event -> {});
			r.getUserSideShape().setOnContextMenuRequested(event -> {});
			Label label = r.getUserSideShape().getLabel();
			label.setOnMouseDragged(event -> {
				this.beingDragged = true;
				label.relocate(event.getX(), event.getY());
			});
			label.setOnMouseReleased(event -> this.beingDragged = false);
		}
		this.topPane.getChildren().setAll(roomShapes);
	}

	//Editor
	// TODO: Automatically use the one floor instead of passing in a collection
	public void displayNodes(Collection<Node> nodes) {
		Set<Circle> nodeShapes = new HashSet<>();
		for (Node n : nodes) {
			nodeShapes.add(n.getShape());
		}
		this.topPane.getChildren().setAll(nodeShapes);

		// Does the same thing, but is hellish to read.
//		this.topPane.getChildren().setAll(this.directory.getNodes().stream().map(Node::getUserSideShape).collect(Collectors.toSet()));
	}

	/**
	 * Recreate and redisplay all lines on this floor
	 */
	public void redrawLines() {
		Set<Line> lines = new HashSet<>();
		for (Node node : directory.getNodesOnFloor(getFloor())) {
			for (Node neighbor : node.getNeighbors()) {
				if ((node.getFloor() == neighbor.getFloor()) &&
						node.getBuildingName().equalsIgnoreCase(neighbor.getBuildingName())) {
					lines.add(new Line(node.getX(), node.getY(), neighbor.getX(), neighbor.getY()));
				}
//				else if (EditorController.DEBUGGING) {
//					Line ln = new Line(node.getX(), node.getY(), neighbor.getX(), neighbor.getY());
//					ln.setStroke(Color.FUCHSIA);
//					lines.add(ln);
//				}
			}
		}
		this.botPane.getChildren().setAll(lines);
	}

	/**
	 * Adds a listener to the choice box.
	 * Allows you to change floors
	 */
	public void initfloorComboBox() {
		this.floorComboBox.setItems(FXCollections.observableArrayList(FloorProxy.getFloors()));
		this.floorComboBox.getSelectionModel().selectedItemProperty().addListener(
				(ignored, ignoredOld, choice) -> this.changeFloor(choice));

		this.floorComboBox.setValue(this.floorComboBox.getItems().get(getFloorNum() - 1)); // default the selection to be whichever floor we start on
	}

	/**
	 * Add listeners to the given node
	 *
	 * @note No other function should add the base listeners to nodes.
	 */
	private void addNodeListeners(Node node) {
		node.getShape().setOnMouseClicked(event -> this.clickNodeListener(event, node));
		node.getShape().setOnMouseDragged(event -> this.dragNodeListener(event, node));
		node.getShape().setOnMouseReleased(event -> this.releaseNodeListener(event, node));
		node.getShape().setOnMousePressed((MouseEvent event) -> {
			this.primaryPressed = event.isPrimaryButtonDown();
			this.secondaryPressed = event.isSecondaryButtonDown();
			this.draggingNode = true;
		});
	}

	private double readX() {
		return Double.parseDouble(this.xCoordField.getText());
	}

	private double readY() {
		return Double.parseDouble(this.yCoordField.getText());
	}


	/**
	 * Add a new room with the given information to the directory.
	 * Also add a new node associated with the room.
	 */
	private void addNodeRoom(double x, double y, String name, String description) {
		// checking to see if x and y are negative or name field is empty. Changes text
		// next to each textField to red if it breaks the rules.
		if(x < 0 || y < 0 || name.isEmpty()) {
			if(x < 0){
				xPos.setTextFill(Color.RED);
			} else {
				xPos.setTextFill(Color.BLACK);
			} if(y < 0){
				yPos.setFill(Color.RED);
			} else {
				yPos.setFill(Color.BLACK);
			} if(name.isEmpty()){
				roomName.setFill(Color.RED);
			} else {
				roomName.setFill(Color.BLACK);
			}
			return;
		}
		xPos.setTextFill(Color.BLACK);
		yPos.setFill(Color.BLACK);
		roomName.setFill(Color.BLACK);

		// This first condition requires that there has only been one node selected
		// TODO: Review this assumption
		if (this.selectedNodes.size() == 1 && this.selectedNodes.get(0).getRoom() == null) {
			directory.addNewRoomToNode(this.selectedNodes.get(0), name, description);
		} else {
			Node newNode = directory.addNewRoomNode(x, y, getFloor(), name, description);
			this.addNodeListeners(newNode);
			this.redisplayGraph();
			this.selectedNodes.forEach(n -> {
				this.directory.connectOrDisconnectNodes(n, newNode);
			});
			this.redisplayAll();
		}
	}

	/** Add a new node to the directory at the given coordinates */
	private void addNode(double x, double y) {
		if(x < 0 || y < 0) {
			return;
		}
		Node newNode = this.directory.addNewNode(x, y, getFloor());
		this.addNodeListeners(newNode);

		int size = this.selectedNodes.size();
		if(size > 0) {
			this.directory.connectOrDisconnectNodes(this.selectedNodes.get(size - 1), newNode);
		}
		if(this.shiftPressed) {
			this.selectOrDeselectNode(newNode);
		}
	}

	/**
	 * This should only be called by the modify room button
	 *
	 * it runs with the assumption that it has been guaranteed a room is in the selected Nodes list at index 0
	 *
	 * DO NOT USE IT IF YOU HAVE NOT SATISFIED THIS REQUIREMENT
	 */
	private void updateSelectedRoom(double x, double y, String name, String description) {
		this.selectedNodes.get(0).applyToRoom(room -> {
			directory.updateRoom(room, name, description);
			// TODO: Handle this in updateRoom or a method called there (VERY BAD)
			((Text)room.getUserSideShape().getChildren().get(1)).setText(name);
		});
		this.updateSelectedNode(x, y);
		this.redrawLines();
		// TODO: Update the location of the node, whether or not it is a room (or not)
		// That should just require a Node.moveTo (but make sure that's not being made private first)
	}

	/**
	 * This should only be called in theory by the method called by the modify room button.
	 *
	 * It runs with the assumption that it has been guaranteed that a node is in the selectedNodes list at index 0
	 *
	 * DO NOT USE IT IF YOU HAVE NOT SATISFIED THIS REQUIREMENT
	 */
	private void updateSelectedNode(double x, double y) {
		this.selectedNodes.get(0).moveTo(x, y);
		this.selectedNodes.get(0).getShape().setCenterX(this.selectedNodes.get(0).getX());
		this.selectedNodes.get(0).getShape().setCenterY(this.selectedNodes.get(0).getY());
	}

	private void updateSelectedNodes(double x, double y) {
		this.selectedNodes.forEach(n -> {
			double newX = n.getX() - this.clickedX + x;
			double newY = n.getY() - this.clickedY + y;
			n.moveTo(newX, newY);
			n.getShape().setCenterX(newX);
			n.getShape().setCenterY(newY);
		});
		this.clickedX = x;
		this.clickedY = y;
	}

	/**
	 * Deletes the node from EVERYTHING
	 * @param n
	 */
	private void deleteNode(Node n) {
		this.directory.removeNodeAndRoom(n);
	}

	/**
	 * Delete All of the Nodes selected
	 */
	private void deleteSelectedNodes() {
		// I use this instead of a normal forEach because that does not allow for me to remove the nodes easily
		for(int i = this.selectedNodes.size() - 1; i >= 0; i--) {
			Node n = this.selectedNodes.get(i);
			deleteNode(n);
			this.selectedNodes.remove(n);
		}
		this.selectedNodes.clear();
		this.redisplayAll();
	}

	/** Deletes the nodes in the selection pool that are out of bounds (less than 0 x and y)
	 */
	private void deleteOutOfBoundNodes() {

		// I use this instead of a normal forEach because that does not allow for me to remove the nodes easily
		for(int i = this.selectedNodes.size() - 1; i >= 0; i--) {
			Node n = this.selectedNodes.get(i);
			if(n.getX() < 0 || n.getY() < 0) {
				deleteNode(n);
				this.selectedNodes.remove(n);
			}
		}
		this.redisplayAll();
	}


	///////////////////////
	/////EVENT HANDLERS////
	///////////////////////

	public void installPaneListeners(){
		botPane.setOnMouseClicked(e -> {
			this.setFields(e.getX(), e.getY());
			//Create node on double click
			if(e.getClickCount() == 2) {
				this.addNode(e.getX(), e.getY());
			}
			if(!this.shiftPressed) {
				this.deselectNodes();

			}

			this.redisplayGraph();
		});

		// TODO: Move to MapDisplayController
		contentAnchor.setOnScroll(new EventHandler<ScrollEvent>() {
			@Override public void handle(ScrollEvent event) {
				event.consume();
				if (event.getDeltaY() == 0) {
					return;
				}
				double scaleFactor =
						(event.getDeltaY() > 0)
								? SCALE_DELTA
								: 1/SCALE_DELTA;

				if (scaleFactor * SCALE_TOTAL >= 1 && scaleFactor * SCALE_TOTAL <= 6) {
					Bounds viewPort = mapScroll.getViewportBounds();
					Bounds contentSize = contentAnchor.getBoundsInParent();

					double centerPosX = (contentSize.getWidth() - viewPort.getWidth()) * mapScroll.getHvalue() + viewPort.getWidth() / 2;

					double centerPosY = (contentSize.getHeight() - viewPort.getHeight()) * mapScroll.getVvalue() + viewPort.getHeight() / 2;

					mapScroll.setScaleX(mapScroll.getScaleX() * scaleFactor);
					mapScroll.setScaleY(mapScroll.getScaleY() * scaleFactor);
					SCALE_TOTAL *= scaleFactor;

					double newCenterX = centerPosX * scaleFactor;
					double newCenterY = centerPosY * scaleFactor;

					mapScroll.setHvalue((newCenterX - viewPort.getWidth() / 2) / (contentSize.getWidth() * scaleFactor - viewPort.getWidth()));
					mapScroll.setVvalue((newCenterY - viewPort.getHeight() / 2) / (contentSize.getHeight() * scaleFactor - viewPort.getHeight()));
				}

				if (scaleFactor * SCALE_TOTAL <= 1) {
//					SCALE_TOTAL = 1/scaleFactor;
					zoomSlider.setValue(0);

				}else if(scaleFactor * SCALE_TOTAL >= 5.5599173134922495) {
//					SCALE_TOTAL = 6 / scaleFactor;
					zoomSlider.setValue(100);

				}else {
					zoomSlider.setValue(((SCALE_TOTAL - 1)/4.5599173134922495) * 100);
				}

			}
		});

		contentAnchor.setOnMousePressed(e->{
			clickedX = e.getX();
			clickedY = e.getY();
			this.shiftPressed = e.isShiftDown();
			if(this.shiftPressed) {
				this.beingDragged = true;
				this.selectionStartX = e.getX();
				this.selectionStartY = e.getY();
			} else {
				this.beingDragged = false;
			}
		});

		contentAnchor.setOnMouseDragged(e-> {

			this.draggedANode = true;
			if(this.shiftPressed && !draggingNode) {
				Rectangle r = new Rectangle();
				if(e.getX() > selectionStartX) {
					r.setX(selectionStartX);
					r.setWidth(e.getX() - selectionStartX);
				} else {
					r.setX(e.getX());
					r.setWidth(selectionStartX - e.getX());
				}
				if(e.getY() > selectionStartY) {
					r.setY(selectionStartY);
					r.setHeight(e.getY() - selectionStartY);
				} else {
					r.setY(e.getY());
					r.setHeight(selectionStartY - e.getY());
				}
				r.setFill(Color.SKYBLUE);
				r.setStroke(Color.BLUE);
				r.setOpacity(0.5);
				this.redisplayAll();
				this.botPane.getChildren().add(r);
			}

			if(!beingDragged && !this.toggleShowRooms) {
				contentAnchor.setTranslateX(contentAnchor.getTranslateX() + e.getX() - clickedX);
				contentAnchor.setTranslateY(contentAnchor.getTranslateY() + e.getY() - clickedY);
			} else if(this.toggleShowRooms) {


			}
			e.consume();
		});

		contentAnchor.setOnMouseReleased(e->{
			if(this.shiftPressed && !this.draggingNode) { // this is so that you are allowed to release shift after pressing it at the start of the drag
				this.selectionEndX = e.getX();
				this.selectionEndY = e.getY();
				this.redisplayAll(); // this is to clear the rectangle off of the pane

				// These are the bounds of the selection
				double topLeftX;
				double topLeftY;
				double botRightX;
				double botRightY;
				if(this.selectionStartX < this.selectionEndX) {
					topLeftX = this.selectionStartX;
					botRightX = this.selectionEndX;
				} else {
					topLeftX = this.selectionEndX;
					botRightX = this.selectionStartX;
				}
				if(this.selectionStartY < this.selectionEndY) {
					topLeftY = this.selectionStartY;
					botRightY = this.selectionEndY;
				} else {
					topLeftY = this.selectionEndY;
					botRightY = this.selectionStartY;
				}
				// Loop through and select/deselect all nodes in the bounds
				this.directory.getNodesOnFloor(getFloor()).forEach(n -> {
					if(n.getX() > topLeftX && n.getX() < botRightX && n.getY() > topLeftY && n.getY() < botRightY) {
						// Within the bounds, select or deselect it
						this.selectOrDeselectNode(n);
					}
				});
			}
			if(this.toggleShowRooms) {
				this.displayAdminSideRooms();
			}
			this.shiftPressed = e.isShiftDown();
			this.beingDragged = this.shiftPressed;
			this.draggingNode = false;
		});
	}

	public void clickNodeListener(MouseEvent e, Node n) {
		// update text fields
		this.setFields(n.getX(), n.getY());
		if(this.draggedANode) {
			this.draggedANode = false;
			return;
		}
		// check if you single click
		// so, then you are selecting a node
		if(e.getClickCount() == 1 && this.primaryPressed) {
			if(!this.shiftPressed) {
				if(this.selectedNodes.size() > 1) {
					this.deselectNodes();
				} else if(this.selectedNodes.size() == 1 && !this.selectedNodes.get(0).equals(n)) {
					this.deselectNodes();
				}
			}
			// This ctrls stuff for pressing ctrl when you clicked
			// SELECTS ALL OF THE NODE's NEIGHBORS
			if(e.isControlDown()) {
				n.getNeighbors().forEach(neighbor-> {
					this.selectOrDeselectNode(neighbor);
				});
			}
			this.selectOrDeselectNode(n);
			this.updateFields();

		} else if(this.selectedNodes.size() != 0 && this.secondaryPressed) {
			/**
			 * Connect all of the nodes selected to the one that you have clicked on
			 */



			this.selectedNodes.forEach(nodes->{
				this.directory.connectOrDisconnectNodes(nodes, n);
			});
			this.redrawLines();
		}
	}

	// This is going to allow us to drag a node!!!
	public void dragNodeListener(MouseEvent e, Node n) {
		this.beingDragged = true;
		this.draggingNode = true;
		if(this.selectedNodes.size() != 0 && this.selectedNodes.contains(n)) {
			if(e.isPrimaryButtonDown()) {
				this.updateSelectedNodes(e.getX(), e.getY());
				this.setFields(n.getX(), n.getY());
				this.redrawLines();

			} else if (this.secondaryPressed) {
				// right click drag on the selected node
				// do nothing for now
			}
		}
	}

	public void releaseNodeListener(MouseEvent e, Node n) {
		this.releasedX = e.getX();
		this.releasedY = e.getY();

		// if the releasedX or Y is negative we want to remove the node

		// Delete any nodes that were dragged out of bounds
		this.deleteOutOfBoundNodes();

		this.beingDragged = false;
	}

	@FXML
	protected void increaseZoomButtonPressed() {
		double zoomPercent = (zoomSlider.getValue()/100);
		zoomPercent+=.2;
		zoomPercent = (zoomPercent > 1 ? 1 : zoomPercent);
		zoomSlider.setValue(zoomPercent*100);
		double zoomCoefficient = zoomMin*(1 - zoomPercent) + zoomMax*(zoomPercent);
		contentAnchor.setScaleX(zoomCoefficient);
		contentAnchor.setScaleY(zoomCoefficient);
	}

	@FXML
	protected void decreaseZoomButtonPressed() {
		double zoomPercent = (zoomSlider.getValue()/100);
		zoomPercent-=.2;
		zoomPercent = (zoomPercent < 0 ? 0 : zoomPercent);
		zoomSlider.setValue(zoomPercent*100);
		double zoomCoefficient = zoomMin*(1 - zoomPercent) + zoomMax*(zoomPercent);
		contentAnchor.setScaleX(zoomCoefficient);
		contentAnchor.setScaleY(zoomCoefficient);
	}

	/**
	 * Adds or removes the node from the selection pool
	 * @param n - The Node being selected or deselected
	 */
	private void selectOrDeselectNode(Node n) {
		if(this.selectedNodes.contains(n)) {
			this.selectedNodes.remove(n);
			this.iconController.resetSingleNode(n);
		} else {
			this.selectedNodes.add(n);
			this.iconController.selectAnotherNode(n);
		}
		this.redisplayGraph();
		System.out.println(this.selectedNodes.size()); // For debugging
	}

	private void selectAllNodesOnFloor() {
		this.directory.getNodesOnFloor(floor).forEach(node -> {
			if (!this.selectedNodes.contains(node)) {
				this.selectedNodes.add(node);
				this.iconController.selectAnotherNode(node);
			}
		});
	}

	private void deselectNodes() {
		this.iconController.deselectAllNodes();
		this.selectedNodes.clear();
		System.out.println(this.selectedNodes.size());
	}

	// This method is commented out because it is outdated and was only used when there was singular node selection
	// In the current implementation it is not needed
//	private void deselectNode(){
//		this.selectedNode = null;
//		this.iconController.deselectAllNodes();
//		this.redisplayGraph();
//	}

	private void setXCoordField(double x) {
		this.xCoordField.setText(x+"");
	}

	private void setYCoordField(double y) {
		this.yCoordField.setText(y+"");
	}

	private void setFields(double x, double y) {
		this.setXCoordField(x);
		this.setYCoordField(y);
	}

	private void setNameField(String name) {
		this.nameField.setText(name);
	}

	private void setDescriptField(String desc) {
		this.descriptField.setText(desc);
	}

	private void setRoomFields(String name, String desc) {
		this.setNameField(name);
		this.setDescriptField(desc);
	}

	/** Updates the node/room fields based off of the most recently selected node (or room)
	 *
	 */
	private void updateFields() {
		if(selectedNodes.size() == 0) {
			return;
		}
		this.setFields(selectedNodes.get(this.selectedNodes.size() - 1).getX(), selectedNodes.get(this.selectedNodes.size() - 1).getY());

		selectedNodes.get(this.selectedNodes.size() - 1).applyToRoom(room ->
				this.setRoomFields(room.getName(), room.getDescription()));
	}

	/**
	 * Upload professonals from a file
	 */
	@FXML
	private void loadProfessionalsFile() {
		Alert ask = new Alert(Alert.AlertType.CONFIRMATION, "If the selected file "
				+ "contains people who are already in the application, they will be duplicated.");

		// true if and only if the button pressed in the alert said "OK"
		if (ask.showAndWait().map(result -> "OK".equals(result.getText())).orElse(false)) {
			FileChooser fc = new FileChooser();
			File f = fc.showOpenDialog(this.contentAnchor.getScene().getWindow());
			if (f != null) {
				try {
					FileParser.parseProfessionals(f, directory);
				} catch (FileNotFoundException e) {
					Alert a = new Alert(Alert.AlertType.ERROR, "Unable to read file");
					a.showAndWait();
					return;
				}
				this.populateTableView();
			}
		}
	}

	/**
	 * Get a choice box that sets the active algorithm
	 */
	private void setUpAlgorithmChoiceBox() {
		this.algorithmComboBox.setItems(FXCollections.observableArrayList(Pathfinder.getAlgorithmList()));
		this.algorithmComboBox.getSelectionModel().selectedItemProperty().addListener(
				(ignored, ignoredOld, choice) -> Pathfinder.setStrategy(choice));
		// this.algorithmComboBox.setConverter(Algorithm.ALGORITHM_STRING_CONVERTER);
		this.algorithmComboBox.getSelectionModel().select(Pathfinder.getStrategy());
	}

	/*
	To set the kiosk, bind this line to a "set kiosk" button:
	if (selectedNode != null) selectedNode.applyToRoom(room -> directory.setKiosk(room));
	 */

	@FXML
	public void setToggleShowRooms() {
		this.toggleShowRooms = !toggleShowRooms;
		if(toggleShowRooms) {
			// for now, disable dragging
			this.imageViewMap.setDisable(true);
			this.botPane.setDisable(true);
			this.botPane.getChildren().clear();
			this.topPane.getChildren().clear();
			this.displayAdminSideRooms();

		} else {
			// re-enable dragging
			this.imageViewMap.setDisable(false);
			this.botPane.setDisable(false);
			this.redisplayAll();
		}
	}

	/**
	 * Show the rooms with editable labels to the admin
	 */
	public void displayAdminSideRooms() {
		Set<javafx.scene.Node> roomShapes = new HashSet<>();
		for (Room room : directory.getRoomsOnFloor(floor)) {
			roomShapes.add(room.getAdminSideShape());
			/* This is code to make a context menu appear when you right click on the shape for a room
			 * setonContextMenuRequested pretty much checks the right click- meaning right clicking is how you request a context menu
			 * that is reallllllllly helpful for a lot of stuff
			 */
		}
		this.topPane.getChildren().setAll(roomShapes);
	}

	/*
	To set the kiosk, bind this line to a "set kiosk" button
	*/
	@FXML
	public void selectKioskClicked() {
		if (selectedNodes.size() == 1) selectedNodes.get(0).applyToRoom(room -> directory.setKiosk(room));
	}
	@FXML
	private void helpBtnClicked() throws IOException {
		AdminHelpController helpController = new AdminHelpController();
		FXMLLoader loader = new FXMLLoader();
		loader.setLocation(this.getClass().getResource("/AdminHelp.fxml"));
		Scene helpScene = new Scene(loader.load());
		Stage helpStage = new Stage();
		helpStage.initOwner(contentAnchor.getScene().getWindow());
		helpStage.setScene(helpScene);
		helpStage.showAndWait();
	}
}
