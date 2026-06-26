package com.example.studyfactory.domain.room.config;

import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.room.entity.Room;
import com.example.studyfactory.domain.room.entity.Seat;
import com.example.studyfactory.domain.room.entity.SeatType;
import com.example.studyfactory.domain.room.repository.RoomRepository;
import com.example.studyfactory.domain.room.repository.SeatRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RoomLayoutDataInitializer implements ApplicationRunner {

    private static final String BRANCH_NAME = "망미점";

    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Branch branch = branchRepository.findByName(BRANCH_NAME)
                .orElseGet(() -> branchRepository.save(new Branch(BRANCH_NAME)));
        seatRepository.deleteByBranchId(branch.getId());

        Room firstRoom = findOrCreateRoom(branch.getId(), "1작업실", 14, 7);
        resetSeats(firstRoom, firstRoomSeats());

        Room secondRoom = findOrCreateRoom(branch.getId(), "2작업실", 12, 8);
        resetSeats(secondRoom, secondRoomSeats());
    }

    private Room findOrCreateRoom(Long branchId, String name, int rows, int cols) {
        return roomRepository.findByBranchIdAndName(branchId, name)
                .map(room -> {
                    room.updateLayout(rows, cols);
                    return room;
                })
                .orElseGet(() -> roomRepository.save(new Room(branchId, name, rows, cols)));
    }

    private void resetSeats(Room room, List<LayoutItem> items) {
        List<Seat> seats = new ArrayList<>();
        for (LayoutItem item : items) {
            seats.add(new Seat(room.getBranchId(), room.getId(), item.number(), null, item.type(), item.y(), item.x()));
        }
        seatRepository.saveAll(seats);
    }

    private List<LayoutItem> firstRoomSeats() {
        return List.of(
                seat(54, 1, 1),
                seat(53, 1, 2), seat(52, 2, 2), seat(51, 3, 2), seat(50, 4, 2), seat(49, 5, 2), seat(48, 6, 2),
                seat(47, 1, 3), seat(46, 2, 3), seat(45, 3, 3), seat(44, 4, 3), seat(43, 5, 3), seat(7, 7, 3),
                seat(42, 1, 4), seat(41, 2, 4), seat(40, 3, 4), seat(39, 4, 4), seat(38, 5, 4), seat(6, 7, 4),
                seat(5, 7, 5),
                seat(37, 1, 6), seat(36, 2, 6), seat(35, 3, 6), seat(34, 4, 6), seat(33, 5, 6), seat(4, 7, 6),
                seat(32, 1, 7), seat(31, 2, 7), seat(30, 3, 7), seat(29, 4, 7), seat(28, 5, 7), seat(3, 7, 7),
                seat(2, 7, 8),
                seat(27, 1, 9), seat(26, 2, 9), seat(25, 3, 9), seat(24, 4, 9), seat(23, 5, 9), seat(1, 7, 9),
                seat(22, 1, 10), seat(21, 2, 10), seat(20, 3, 10), seat(19, 4, 10), seat(18, 5, 10),
                seat(16, 1, 12), seat(14, 2, 12), seat(12, 3, 12), seat(10, 4, 12), seat(8, 5, 12), door(6, 12),
                seat(17, 1, 13), seat(15, 2, 13), seat(13, 3, 13), seat(11, 4, 13), seat(9, 5, 13)
        );
    }

    private List<LayoutItem> secondRoomSeats() {
        return List.of(
                seat(83, 1, 1),
                seat(82, 1, 2), seat(81, 2, 2), seat(80, 3, 2), seat(79, 4, 2), seat(84, 5, 2), seat(85, 6, 2), seat(86, 7, 2), seat(87, 8, 2),
                seat(78, 1, 3), seat(77, 2, 3), seat(76, 3, 3), seat(75, 4, 3),
                seat(74, 1, 5), seat(73, 2, 5), seat(72, 3, 5), seat(71, 4, 5), seat(88, 6, 5), seat(89, 7, 5), seat(90, 8, 5),
                seat(70, 1, 6), seat(69, 2, 6), seat(68, 3, 6), seat(67, 4, 6), seat(91, 6, 6), seat(92, 7, 6), seat(93, 8, 6),
                seat(66, 1, 8), seat(65, 2, 8), seat(64, 3, 8), seat(63, 4, 8), seat(94, 6, 8), seat(95, 7, 8), seat(96, 8, 8),
                seat(62, 1, 9), seat(61, 2, 9), seat(60, 3, 9), seat(59, 4, 9), seat(97, 6, 9), seat(98, 7, 9), seat(99, 8, 9),
                seat(58, 1, 11), seat(57, 2, 11), seat(56, 3, 11), seat(55, 4, 11), door(5, 11), seat(100, 6, 11), seat(101, 7, 11), seat(102, 8, 11)
        );
    }

    private LayoutItem seat(Integer number, int x, int y) {
        return new LayoutItem(number, SeatType.SEAT, x, y);
    }

    private LayoutItem door(int x, int y) {
        return new LayoutItem(null, SeatType.DOOR, x, y);
    }

    private record LayoutItem(Integer number, SeatType type, int x, int y) {
    }
}
